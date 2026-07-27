package com.penmate.backend.application.agent.tool.runtime;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;

import static org.assertj.core.api.Assertions.assertThat;

class ToolApprovalPreviewTest {

    @Test
    void previews_an_atomic_node_batch_without_copying_private_content() {
        var preview = new ToolApprovalPreview(new JacksonJsonCodec(new ObjectMapper()),
                java.util.List.of(new StoryBibleV2ApprovalPreviewConfiguration()
                        .storyBibleNodeWriteApprovalPreview()))
                .from("story_bible_node_write", """
                {"items":[
                  {"operation":"update","nodeId":71,"expectedRevision":4,"title":"Mira","bodyMarkdown":"private body"},
                  {"operation":"create","title":"North Gate","summary":"private summary"}
                ]}
                """);

        assertThat(preview)
                .containsEntry("执行方式", "原子批次，任一项失败则全部取消")
                .containsEntry("变更数量", "2 项")
                .containsEntry("变更 1", "update · Mira · 字段 bodyMarkdown, title · 基于修订 4")
                .containsEntry("变更 2", "create · North Gate · 字段 summary, title");
        assertThat(preview.values()).allSatisfy(value -> assertThat(value)
                .doesNotContain("private body", "private summary"));
    }
}
