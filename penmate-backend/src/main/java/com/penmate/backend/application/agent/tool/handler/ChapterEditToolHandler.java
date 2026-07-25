package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationCancelledException;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ChapterEditToolHandler implements AgentToolHandler {

    private static final long LEASE_RENEW_INTERVAL_NANOS = 30_000_000_000L;
    private static final long PREVIEW_FLUSH_INTERVAL_NANOS = 100_000_000L;
    private static final int MAX_PENDING_PREVIEW_CHARS = 256;
    private static final int LEASE_ACQUIRE_ATTEMPTS = 20;
    private static final long LEASE_ACQUIRE_RETRY_MILLIS = 250L;

    private final NovelApplicationService novels;
    private final AgentModelRoutingService modelRouting;
    private final AgentLlmInvocationService llmInvocations;
    private final AgentRunEventPublisher events;
    private final JsonCodec jsonCodec;

    public ChapterEditToolHandler(NovelApplicationService novels,
                                  AgentModelRoutingService modelRouting,
                                  AgentLlmInvocationService llmInvocations,
                                  AgentRunEventPublisher events,
                                  JsonCodec jsonCodec) {
        this.novels = novels;
        this.modelRouting = modelRouting;
        this.llmInvocations = llmInvocations;
        this.events = events;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public String toolCode() {
        return "chapter_edit";
    }

    @Override
    public boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) {
        return true;
    }

    @Override
    public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        if (request == null || context == null) {
            throw new IllegalArgumentException("chapter_edit requires project, run and operator context");
        }
        Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
        if (context.input().chapterId() == null) throw new IllegalArgumentException("active chapter is required");
        String instruction = JsonValues.string(args, "instruction");
        if (instruction == null || instruction.isBlank()) throw new IllegalArgumentException("instruction is required");
        for (String field : args.keySet()) {
            if (!"instruction".equals(field)) {
                throw new IllegalArgumentException("Unexpected chapter_edit field: " + field);
            }
        }
    }

    @Override
    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
        Long chapterId = context.input().chapterId();
        String instruction = JsonValues.string(args, "instruction").trim();
        NovelApplicationService.AiChapterLeaseView lease = null;
        PreviewStream preview = null;
        try {
            lease = acquireLeaseWithWait(context, chapterId);
            if (!lease.editable()) {
                publish(context.runId(), "chapter.edit.failed", eventPayload(context, request, chapterId,
                        "errorCode", "CHAPTER_AI_BUSY", "errorMessage", lease.reason()));
                return ToolCallResult.failed("CHAPTER_AI_BUSY", lease.reason());
            }
            publish(context.runId(), "chapter.edit.started", eventPayload(context, request, chapterId,
                    "contentRevision", lease.contentRevision(), "leaseExpiresAt", lease.expiresAt()));

            AgentLlmExecutionConfig executionConfig = modelRouting.resolveExecutionConfig(
                    context.ownerUserId(), null, context.traceId());
            preview = new PreviewStream(context, request, chapterId, lease.leaseToken());
            AgentLlmTurnResponse response = llmInvocations.invokeStreaming(
                    context.runId(),
                    new AgentLlmTurnRequest(
                            List.of(AgentLlmMessage.user(buildPrompt(instruction, lease.content()))),
                            List.of(),
                            "none"
                    ),
                    executionConfig,
                    preview::accept
            );
            String finalContent = requireContent(response.assistantText());
            preview.complete(finalContent);
            NovelApplicationService.AiChapterEditResult saved = novels.saveAiChapterEdit(
                    context.projectId(), chapterId, context.ownerUserId(), context.runId(), request.toolCallId(),
                    lease.leaseToken(), lease.contentRevision(), finalContent);
            releaseQuietly(context, chapterId, lease);
            lease = null;
            publish(context.runId(), "chapter.edit.completed", eventPayload(context, request, chapterId,
                    "operationId", String.valueOf(saved.undo().operationId()),
                    "contentRevision", saved.chapter().getContentRevision(),
                    "wordCount", saved.chapter().getWordCount(),
                    "expiresAt", saved.undo().expiresAt()));

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("chapterId", String.valueOf(chapterId));
            output.put("operationId", String.valueOf(saved.undo().operationId()));
            output.put("contentRevision", saved.chapter().getContentRevision());
            output.put("wordCount", saved.chapter().getWordCount());
            output.put("undoExpiresAt", saved.undo().expiresAt());
            return ToolCallResult.success(jsonCodec.write(output));
        } catch (AgentLlmInvocationCancelledException ex) {
            if (preview != null) preview.flushPending();
            releaseQuietly(context, chapterId, lease);
            lease = null;
            publish(context.runId(), "chapter.edit.cancelled", eventPayload(context, request, chapterId));
            return ToolCallResult.failed("CHAPTER_EDIT_CANCELLED", "Chapter edit was cancelled");
        } catch (Exception ex) {
            if (preview != null) preview.flushPending();
            String message = rootMessage(ex);
            releaseQuietly(context, chapterId, lease);
            lease = null;
            publish(context.runId(), "chapter.edit.failed", eventPayload(context, request, chapterId,
                    "errorCode", "CHAPTER_EDIT_FAILED", "errorMessage", message));
            log.warn("chapter_edit failed: projectId={}, chapterId={}, runId={}, message={}",
                    context.projectId(), chapterId, context.runId(), message);
            return ToolCallResult.failed("CHAPTER_EDIT_FAILED", message);
        } finally {
            releaseQuietly(context, chapterId, lease);
        }
    }

    private NovelApplicationService.AiChapterLeaseView acquireLeaseWithWait(AuthorizedAgentRunContext context,
                                                                             Long chapterId) {
        NovelApplicationService.AiChapterLeaseView lease = null;
        for (int attempt = 0; attempt < LEASE_ACQUIRE_ATTEMPTS; attempt++) {
            lease = novels.acquireChapterAiLease(
                    context.projectId(), chapterId, context.ownerUserId(), context.runId());
            if (lease.editable()) return lease;
            if (attempt + 1 < LEASE_ACQUIRE_ATTEMPTS) {
                try {
                    Thread.sleep(LEASE_ACQUIRE_RETRY_MILLIS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AgentLlmInvocationCancelledException();
                }
            }
        }
        return lease;
    }

    private void releaseQuietly(AuthorizedAgentRunContext context, Long chapterId,
                                NovelApplicationService.AiChapterLeaseView lease) {
        if (lease == null || !lease.editable() || lease.leaseToken() == null) return;
        try {
            novels.releaseChapterAiLease(context.projectId(), chapterId, context.ownerUserId(), lease.leaseToken());
        } catch (Exception ex) {
            log.warn("chapter_edit lease release failed: chapterId={}, runId={}", chapterId, context.runId(), ex);
        }
    }

    private String buildPrompt(String instruction, String originalContent) {
        return """
                你正在执行小说章节正文编辑。请严格根据修改要求处理原文，并只输出修改后的完整章节正文。
                不要解释，不要添加 Markdown 代码围栏，也不要省略未修改的段落。

                修改要求：
                %s

                原章节正文：
                %s
                """.formatted(instruction, originalContent == null ? "" : originalContent).trim();
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) throw new IllegalStateException("Model returned empty chapter content");
        return content;
    }

    private void publish(Long runId, String eventType, Map<String, Object> payload) {
        events.publish(runId, eventType, payload);
    }

    private Map<String, Object> eventPayload(AuthorizedAgentRunContext context, ToolCallRequest request,
                                             Long chapterId, Object... additions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", String.valueOf(context.projectId()));
        payload.put("chapterId", String.valueOf(chapterId));
        payload.put("runId", String.valueOf(context.runId()));
        payload.put("toolCallId", request.toolCallId());
        for (int index = 0; index < additions.length; index += 2) {
            payload.put((String) additions[index], additions[index + 1]);
        }
        return payload;
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private final class PreviewStream {
        private final ToolCallRequest request;
        private final AuthorizedAgentRunContext context;
        private final Long chapterId;
        private final String leaseToken;
        private final StringBuilder accumulated = new StringBuilder();
        private final StringBuilder pending = new StringBuilder();
        private long lastFlushNanos;
        private long lastLeaseRenewNanos = System.nanoTime();

        private PreviewStream(AuthorizedAgentRunContext context, ToolCallRequest request,
                              Long chapterId, String leaseToken) {
            this.context = context;
            this.request = request;
            this.chapterId = chapterId;
            this.leaseToken = leaseToken;
        }

        private synchronized void accept(String text) {
            if (text == null || text.isEmpty()) return;
            accumulated.append(text);
            pending.append(text);
            long now = System.nanoTime();
            if (now - lastLeaseRenewNanos >= LEASE_RENEW_INTERVAL_NANOS) {
                novels.renewChapterAiLease(context.projectId(), chapterId, context.ownerUserId(), leaseToken);
                lastLeaseRenewNanos = now;
            }
            if (lastFlushNanos == 0L || now - lastFlushNanos >= PREVIEW_FLUSH_INTERVAL_NANOS
                    || pending.length() >= MAX_PENDING_PREVIEW_CHARS) {
                flush(now);
            }
        }

        private synchronized void complete(String finalContent) {
            if (accumulated.isEmpty() && finalContent != null && !finalContent.isEmpty()) {
                accumulated.append(finalContent);
                pending.append(finalContent);
            } else if (!accumulated.toString().equals(finalContent)) {
                accumulated.setLength(0);
                accumulated.append(finalContent);
                pending.setLength(0);
                events.broadcastOnly(context.runId(), "chapter.edit.snapshot", eventPayload(context, request, chapterId,
                        "content", finalContent, "offset", 0, "contentLength", finalContent.length()), -1L);
                return;
            }
            flush(System.nanoTime());
        }

        private synchronized void flushPending() {
            flush(System.nanoTime());
        }

        private void flush(long now) {
            if (pending.isEmpty()) return;
            int offset = accumulated.length() - pending.length();
            String chunk = pending.toString();
            pending.setLength(0);
            events.broadcastOnly(context.runId(), "chapter.edit.delta", eventPayload(context, request, chapterId,
                    "text", chunk, "offset", offset, "contentLength", accumulated.length()), -1L);
            lastFlushNanos = now;
        }
    }
}
