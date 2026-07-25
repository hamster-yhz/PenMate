package com.penmate.backend.application.agent.tool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static com.penmate.backend.application.agent.tool.runtime.AgentToolTestContext.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterContentToolHandlersTest {
    private final NovelApplicationService novels = mock(NovelApplicationService.class);
    private final AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void reads_exact_content_with_revision_and_hash() {
        NovelChapter chapter = chapter("原文\n第二行", 4L);
        when(novels.getChapter(9001L, 3001L)).thenReturn(chapter);
        ChapterReadToolHandler handler = new ChapterReadToolHandler(novels, jsonCodec);

        var result = handler.execute(context(), request("chapter_read", "{}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("\"content\":\"原文\\n第二行\"")
                .contains("\"contentRevision\":4")
                .contains("\"contentHash\":\"" + ChapterToolSupport.sha256("原文\n第二行") + "\"");
        verify(novels).getChapter(9001L, 3001L);
    }

    @Test
    void read_rejects_model_supplied_chapter_identity() {
        ChapterReadToolHandler handler = new ChapterReadToolHandler(novels, jsonCodec);

        assertThatThrownBy(() -> handler.validate(context(), request("chapter_read", "{\"chapterId\":99}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accepts no fields");
    }

    @Test
    void replaces_content_verbatim_and_returns_a_verifiable_receipt() {
        String before = "旧正文";
        String after = "新正文\n保持原样";
        stubLease(before, 4L);
        when(novels.saveAiChapterEdit(eq(9001L), eq(3001L), eq(1001L), eq(8001L), eq("call-1"),
                eq("lease-1"), eq(4L), eq(after))).thenReturn(saved(after, 5L));
        ChapterReplaceToolHandler handler = new ChapterReplaceToolHandler(novels, jsonCodec, events);

        var result = handler.execute(context(), request("chapter_replace", """
                {"expectedRevision":4,"expectedContentHash":"%s","content":"新正文\\n保持原样"}
                """.formatted(ChapterToolSupport.sha256(before))));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("\"beforeRevision\":4", "\"afterRevision\":5", "\"changed\":true")
                .contains("\"beforeContentHash\":\"" + ChapterToolSupport.sha256(before) + "\"")
                .contains("\"afterContentHash\":\"" + ChapterToolSupport.sha256(after) + "\"")
                .contains("\"operationId\":\"7001\"");
        verify(novels).releaseChapterAiLease(9001L, 3001L, 1001L, "lease-1");
    }

    @Test
    void rejects_stale_hash_without_writing() {
        stubLease("current", 4L);
        ChapterReplaceToolHandler handler = new ChapterReplaceToolHandler(novels, jsonCodec, events);

        var result = handler.execute(context(), request("chapter_replace", """
                {"expectedRevision":4,"expectedContentHash":"%s","content":"replacement"}
                """.formatted("0".repeat(64))));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("CHAPTER_CONTENT_CONFLICT");
        verify(novels, never()).saveAiChapterEdit(anyLong(), anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void patch_applies_ordered_exact_replacements_in_one_write() {
        String before = "甲乙甲";
        stubLease(before, 7L);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        when(novels.saveAiChapterEdit(eq(9001L), eq(3001L), eq(1001L), eq(8001L), eq("call-1"),
                eq("lease-1"), eq(7L), content.capture())).thenAnswer(invocation -> saved(content.getValue(), 8L));
        ChapterPatchToolHandler handler = new ChapterPatchToolHandler(novels, jsonCodec, events);

        var result = handler.execute(context(), request("chapter_patch", """
                {"expectedRevision":7,"expectedContentHash":"%s","replacements":[
                  {"oldText":"甲","newText":"丙","expectedOccurrences":2},
                  {"oldText":"丙乙丙","newText":"完成","expectedOccurrences":1}
                ]}
                """.formatted(ChapterToolSupport.sha256(before))));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(content.getValue()).isEqualTo("完成");
        assertThat(result.toolOutput()).contains("\"replacementsApplied\":3");
    }

    @Test
    void patch_mismatch_rejects_the_entire_operation_before_persistence() {
        String before = "唯一文本";
        stubLease(before, 2L);
        ChapterPatchToolHandler handler = new ChapterPatchToolHandler(novels, jsonCodec, events);

        var result = handler.execute(context(), request("chapter_patch", """
                {"expectedRevision":2,"expectedContentHash":"%s","replacements":[
                  {"oldText":"唯一","newText":"第一步","expectedOccurrences":1},
                  {"oldText":"不存在","newText":"第二步","expectedOccurrences":1}
                ]}
                """.formatted(ChapterToolSupport.sha256(before))));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("CHAPTER_PATCH_MISMATCH");
        verify(novels, never()).saveAiChapterEdit(anyLong(), anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void no_op_replace_reports_unchanged_without_creating_an_undo_operation() {
        String before = "不变";
        stubLease(before, 9L);
        ChapterReplaceToolHandler handler = new ChapterReplaceToolHandler(novels, jsonCodec, events);

        var result = handler.execute(context(), request("chapter_replace", """
                {"expectedRevision":9,"expectedContentHash":"%s","content":"不变"}
                """.formatted(ChapterToolSupport.sha256(before))));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("\"beforeRevision\":9", "\"afterRevision\":9", "\"changed\":false")
                .contains("\"operationId\":null");
        verify(novels, never()).saveAiChapterEdit(anyLong(), anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void same_run_can_patch_the_same_chapter_more_than_once_using_each_receipt() {
        String initial = "第一版";
        String second = "第二版";
        String third = "第三版";
        when(novels.acquireChapterAiLease(9001L, 3001L, 1001L, 8001L))
                .thenReturn(
                        new NovelApplicationService.AiChapterLeaseView(
                                true, "lease-1", Instant.parse("2026-07-26T01:00:00Z"), 1L, initial, null),
                        new NovelApplicationService.AiChapterLeaseView(
                                true, "lease-2", Instant.parse("2026-07-26T01:01:00Z"), 2L, second, null));
        when(novels.saveAiChapterEdit(eq(9001L), eq(3001L), eq(1001L), eq(8001L), eq("call-1"),
                eq("lease-1"), eq(1L), eq(second))).thenReturn(saved(second, 2L));
        when(novels.saveAiChapterEdit(eq(9001L), eq(3001L), eq(1001L), eq(8001L), eq("call-2"),
                eq("lease-2"), eq(2L), eq(third))).thenReturn(saved(third, 3L));
        ChapterPatchToolHandler handler = new ChapterPatchToolHandler(novels, jsonCodec, events);

        var first = handler.execute(context(), request("chapter_patch", """
                {"expectedRevision":1,"expectedContentHash":"%s","replacements":[
                  {"oldText":"第一版","newText":"第二版","expectedOccurrences":1}
                ]}
                """.formatted(ChapterToolSupport.sha256(initial)), "call-1"));
        var secondResult = handler.execute(context(), request("chapter_patch", """
                {"expectedRevision":2,"expectedContentHash":"%s","replacements":[
                  {"oldText":"第二版","newText":"第三版","expectedOccurrences":1}
                ]}
                """.formatted(ChapterToolSupport.sha256(second)), "call-2"));

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(first.toolOutput()).contains("\"afterRevision\":2",
                "\"afterContentHash\":\"" + ChapterToolSupport.sha256(second) + "\"");
        assertThat(secondResult.status()).isEqualTo("SUCCESS");
        assertThat(secondResult.toolOutput()).contains("\"beforeRevision\":2", "\"afterRevision\":3",
                "\"afterContentHash\":\"" + ChapterToolSupport.sha256(third) + "\"");
        verify(novels, times(2)).saveAiChapterEdit(eq(9001L), eq(3001L), eq(1001L), eq(8001L),
                anyString(), anyString(), anyLong(), anyString());
    }

    private void stubLease(String content, Long revision) {
        when(novels.acquireChapterAiLease(9001L, 3001L, 1001L, 8001L))
                .thenReturn(new NovelApplicationService.AiChapterLeaseView(
                        true, "lease-1", Instant.parse("2026-07-26T01:00:00Z"), revision, content, null));
    }

    private NovelApplicationService.AiChapterEditResult saved(String content, Long revision) {
        return new NovelApplicationService.AiChapterEditResult(
                chapter(content, revision),
                new NovelApplicationService.AiUndoView(
                        7001L, 8001L, 3001L, "第一章", "AVAILABLE", 1L,
                        Instant.parse("2026-07-25T00:00:00Z"),
                        Instant.parse("2026-07-26T00:00:00Z"), null));
    }

    private NovelChapter chapter(String content, Long revision) {
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(3001L);
        chapter.setProjectId(9001L);
        chapter.setTitle("第一章");
        chapter.setContent(content);
        chapter.setContentRevision(revision);
        chapter.setWordCount(ChapterToolSupport.countWords(content));
        return chapter;
    }

    private ToolCallRequest request(String toolCode, String arguments) {
        return request(toolCode, arguments, "call-1");
    }

    private ToolCallRequest request(String toolCode, String arguments, String toolCallId) {
        return new ToolCallRequest(8001L, toolCode, arguments, "idem-" + toolCallId, toolCallId, 1L);
    }
}
