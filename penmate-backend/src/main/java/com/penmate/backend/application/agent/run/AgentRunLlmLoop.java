package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRunContinuation;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AgentRunLlmLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentRunLlmLoop.class);
    private static final int INITIAL_TURN_INDEX = 1;
    private static final int MAX_ITERATIONS = 10;

    private final AgentLlmInvocationService llmInvocations;
    private final AgentToolDefinitionSource toolDefinitionSource;
    private final AgentRunEventPublisher eventPublisher;
    private final ToolCallApplicationService toolCallService;
    private final AgentCheckpointBoundaryService checkpointBoundary;
    private final AgentRunContinuationArtifactService continuations;
    private final AgentStreamingMessageService streamingMessages;
    private final JsonCodec jsonCodec;

    public AgentRunLlmLoop(AgentLlmInvocationService llmInvocations,
                           AgentToolDefinitionSource toolDefinitionSource,
                           AgentRunEventPublisher eventPublisher,
                           @Lazy ToolCallApplicationService toolCallService,
                           AgentCheckpointBoundaryService checkpointBoundary,
                           AgentRunContinuationArtifactService continuations,
                           AgentStreamingMessageService streamingMessages,
                           JsonCodec jsonCodec) {
        this.llmInvocations = llmInvocations;
        this.toolDefinitionSource = toolDefinitionSource;
        this.eventPublisher = eventPublisher;
        this.toolCallService = toolCallService;
        this.checkpointBoundary = checkpointBoundary;
        this.continuations = continuations;
        this.streamingMessages = streamingMessages;
        this.jsonCodec = jsonCodec;
    }

    public AgentRunLoopResult execute(AgentRunLoopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return executeFrom(request, new ArrayList<>(request.messages()), INITIAL_TURN_INDEX,
                0, LlmTokenUsage.ZERO, new StringBuilder());
    }

    public AgentRunLoopResult resume(AgentRunLoopRequest request, AgentRunContinuation continuation) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(continuation, "continuation must not be null");
        if (!request.runId().equals(continuation.runId())) {
            throw new IllegalArgumentException("Agent Run continuation belongs to another Run");
        }
        List<AgentLlmMessage> messages = new ArrayList<>(continuation.messages());
        StringBuilder assistantText = new StringBuilder(continuation.assistantText());
        return switch (continuation.stage()) {
            case COMPLETED -> AgentRunLoopResult.completed(
                    continuation.assistantText(), continuation.tokenUsage());
            case READY_FOR_LLM -> executeFrom(request, messages, continuation.llmTurnIndex(),
                    continuation.iterationIndex(), continuation.tokenUsage(), assistantText);
            case READY_FOR_TOOL -> resumeToolBatch(request, continuation, messages, assistantText);
        };
    }

    public AgentRunLoopResult resumeApproved(AgentRunLoopRequest request, AgentRunPendingApproval pending) {
        Objects.requireNonNull(pending, "pending must not be null");
        try {
            List<AgentLlmMessage> messages = new ArrayList<>(
                    jsonCodec.readList(pending.resumePayloadJson(), AgentLlmMessage.class));
            Continuation continuation = jsonCodec.read(pending.toolContextJson(), Continuation.class);
            ToolCallResult result = toolCallService.executeToolCall(new ToolCallRequest(
                    pending.projectId(), pending.runId(), pending.sessionId(), pending.turnId(), pending.toolCode(),
                    pending.toolArgsJson(),
                    pending.operatorId() == null ? request.operatorId() : pending.operatorId(),
                    request.traceId(), pending.toolContextJson(),
                    pending.idempotencyKey(), continuation.llmTurnIndex(), pending.toolCallId(), null,
                    pending.resumePayloadJson(), "APPROVED", pending.approvalId().toString(),
                    request.executionToken()
            ));
            if (result == null || !"SUCCESS".equals(result.status())) {
                String message = result == null ? "Approved tool call returned no result" : result.errorMessage();
                return new AgentRunLoopResult(AgentRunLoopResult.Status.FAILED, message,
                        continuation.tokenUsage(), pending.approvalId());
            }
            publishBoundary(request.runId(), "tool.call.completed", eventPayload(
                    "llmTurnIndex", continuation.llmTurnIndex(), "toolCallId", pending.toolCallId(),
                    "toolCode", pending.toolCode(), "toolDisplayName", resolveToolName(pending.toolCode()),
                    "outputPreview", clipText(result.toolOutput(), 200)
            ));
            messages.add(AgentLlmMessage.tool(pending.toolCallId(), result.toolOutput()));
            List<AgentLlmToolCallPayload> siblingCalls = latestAssistantToolCalls(messages);
            int approvedIndex = indexOfToolCall(siblingCalls, pending.toolCallId());
            if (approvedIndex < 0) {
                throw new IllegalStateException("Approved tool call is missing from the saved assistant response");
            }
            AgentRunLoopResult waiting = executeToolBatch(request, messages, siblingCalls,
                    approvedIndex + 1, continuation.llmTurnIndex(), continuation.iterationIndex(),
                    continuation.tokenUsage(), new StringBuilder(continuation.assistantText()), true);
            if (waiting != null) return waiting;
            return executeFrom(request, messages, continuation.llmTurnIndex() + 1,
                    continuation.iterationIndex() + 1, continuation.tokenUsage(),
                    new StringBuilder(continuation.assistantText()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to restore approved Agent tool continuation", ex);
        }
    }

    private AgentRunLoopResult resumeToolBatch(AgentRunLoopRequest request,
                                               AgentRunContinuation continuation,
                                               List<AgentLlmMessage> messages,
                                               StringBuilder assistantText) {
        List<AgentLlmToolCallPayload> toolCalls = latestAssistantToolCalls(messages);
        if (toolCalls.isEmpty() || continuation.nextToolCallIndex() >= toolCalls.size()) {
            throw new IllegalStateException("Agent Run continuation has no recoverable tool call");
        }
        AgentRunLoopResult waiting = executeToolBatch(request, messages, toolCalls,
                continuation.nextToolCallIndex(), continuation.llmTurnIndex(),
                continuation.iterationIndex(), continuation.tokenUsage(), assistantText, true);
        if (waiting != null) return waiting;
        return executeFrom(request, messages, continuation.llmTurnIndex() + 1,
                continuation.iterationIndex() + 1, continuation.tokenUsage(), assistantText);
    }

    private AgentRunLoopResult executeFrom(AgentRunLoopRequest request,
                                           List<AgentLlmMessage> messages,
                                           int initialTurnIndex,
                                           int initialIterationIndex,
                                           LlmTokenUsage initialUsage,
                                           StringBuilder fullAssistantText) {
        int turnIndex = initialTurnIndex;
        LlmTokenUsage totalUsage = initialUsage;

        for (int iteration = initialIterationIndex; iteration < MAX_ITERATIONS; iteration++) {
            saveContinuation(AgentRunContinuation.readyForLlm(
                    request.runId(), messages, turnIndex, iteration,
                    fullAssistantText.toString(), totalUsage));
            eventPublisher.publish(request.runId(), "llm.turn.started", eventPayload(
                    "llmTurnIndex", turnIndex,
                    "traceId", request.traceId()
            ));

            AgentStreamingMessageService.StreamSession streamSession = streamingMessages.open(
                    request.runId(), request.turnId(), turnIndex, fullAssistantText.toString());
            AgentLlmTurnResponse response;
            try {
                response = llmInvocations.invokeStreaming(
                        request.runId(),
                        new AgentLlmTurnRequest(
                                List.copyOf(messages),
                                toolDefinitionSource.listLlmSchemas(),
                                "auto"
                        ),
                        request.executionConfig(),
                        streamSession::accept
                );
                streamSession.complete(response.assistantText());
            } catch (RuntimeException ex) {
                streamSession.flushPending();
                throw ex;
            }

            totalUsage = totalUsage.add(response.tokenUsage());
            fullAssistantText.append(response.assistantText());

            eventPublisher.publish(request.runId(), "llm.turn.completed", Map.of(
                    "llmTurnIndex", turnIndex,
                    "finishReason", response.finishReason(),
                    "toolCallCount", response.toolCalls().size(),
                    "tokenUsage", Map.of(
                            "promptTokens", response.tokenUsage().promptTokens(),
                            "completionTokens", response.tokenUsage().completionTokens(),
                            "totalTokens", response.tokenUsage().totalTokens(),
                            "cachedPromptTokens", response.tokenUsage().cachedPromptTokens(),
                            "cacheCreationPromptTokens", response.tokenUsage().cacheCreationPromptTokens()
                    )
            ));

            if (response.toolCalls().isEmpty()) {
                saveContinuation(AgentRunContinuation.completed(
                        request.runId(), messages, turnIndex, iteration,
                        fullAssistantText.toString(), totalUsage));
                return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
            }

            List<AgentLlmToolCallPayload> toolCalls = response.toolCalls().stream()
                    .map(call -> new AgentLlmToolCallPayload(
                            call.id(), "function", call.toolCode(), call.argumentsJson()))
                    .toList();
            messages.add(AgentLlmMessage.assistant(response.assistantText(), toolCalls));

            AgentRunLoopResult waiting = executeToolBatch(request, messages, toolCalls, 0,
                    turnIndex, iteration, totalUsage, fullAssistantText, false);
            if (waiting != null) return waiting;
            turnIndex++;
        }

        saveContinuation(AgentRunContinuation.completed(
                request.runId(), messages, turnIndex, MAX_ITERATIONS,
                fullAssistantText.toString(), totalUsage));
        return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
    }

    private AgentRunLoopResult executeToolBatch(AgentRunLoopRequest request,
                                                List<AgentLlmMessage> messages,
                                                List<AgentLlmToolCallPayload> toolCalls,
                                                int startIndex,
                                                int turnIndex,
                                                int iterationIndex,
                                                LlmTokenUsage tokenUsage,
                                                StringBuilder fullAssistantText,
                                                boolean recovered) {
        for (int index = startIndex; index < toolCalls.size(); index++) {
            AgentLlmToolCallPayload toolCall = toolCalls.get(index);
            saveContinuation(AgentRunContinuation.readyForTool(
                    request.runId(), messages, turnIndex, iterationIndex, index,
                    fullAssistantText.toString(), tokenUsage));
            String toolName = resolveToolName(toolCall.functionName());
            publishBoundary(request.runId(), "tool.call.started", eventPayload(
                    "llmTurnIndex", turnIndex,
                    "toolCallId", toolCall.id(),
                    "toolCode", toolCall.functionName(),
                    "toolName", toolName,
                    "toolDisplayName", toolName,
                    "iteration", iterationIndex,
                    "recovered", recovered,
                    "argumentsPreview", toolCall.argumentsJson()
            ));

            Continuation context = new Continuation(turnIndex, iterationIndex, tokenUsage,
                    fullAssistantText.toString());
            ToolCallResult result = toolCallService.executeToolCall(new ToolCallRequest(
                    request.projectId(), request.runId(), request.sessionId(), request.turnId(),
                    toolCall.functionName(), toolCall.argumentsJson(), request.operatorId(), request.traceId(),
                    json(context), request.runId() + ":" + toolCall.id(), turnIndex, toolCall.id(),
                    json(toolCalls), json(messages), null, null, request.executionToken()
            ));
            if (result == null) {
                result = ToolCallResult.failed("TOOL_CALL_FAILED", "Tool call returned no result");
            }

            if ("WAITING_APPROVAL".equals(result.status())) {
                publishBoundary(request.runId(), "tool.call.waiting_approval", eventPayload(
                        "llmTurnIndex", turnIndex,
                        "toolCallId", toolCall.id(),
                        "toolCode", toolCall.functionName(),
                        "toolDisplayName", toolName,
                        "approvalId", result.approvalId(),
                        "approvalPreview", result.approvalPreview()
                ));
                log.info("Tool call waiting approval: runId={}, toolCode={}, approvalId={}",
                        request.runId(), toolCall.functionName(), result.approvalId());
                return AgentRunLoopResult.waitingApproval(
                        result.approvalId(), fullAssistantText.toString(), tokenUsage);
            }

            if ("SUCCESS".equals(result.status())) {
                publishBoundary(request.runId(), "tool.call.completed", eventPayload(
                        "llmTurnIndex", turnIndex,
                        "toolCallId", toolCall.id(),
                        "toolCode", toolCall.functionName(),
                        "toolDisplayName", toolName,
                        "outputPreview", clipText(result.toolOutput(), 200)
                ));
                messages.add(AgentLlmMessage.tool(toolCall.id(), result.toolOutput()));
            } else {
                String error = result.errorMessage() == null ? "Unknown error" : result.errorMessage();
                publishBoundary(request.runId(), "tool.call.failed", eventPayload(
                        "llmTurnIndex", turnIndex,
                        "toolCallId", toolCall.id(),
                        "toolCode", toolCall.functionName(),
                        "toolDisplayName", toolName,
                        "errorCode", result.errorCode(),
                        "errorMessage", error
                ));
                messages.add(AgentLlmMessage.tool(toolCall.id(), "Error: " + error));
            }
        }
        return null;
    }

    private void saveContinuation(AgentRunContinuation continuation) {
        AgentRunContinuationArtifactService.ArtifactRef ref = continuations.save(continuation);
        publishBoundary(continuation.runId(), "llm.continuation.saved", Map.of(
                "artifactId", ref.artifactId(),
                "sha256", ref.sha256(),
                "sizeBytes", ref.sizeBytes(),
                "stage", continuation.continuationStage(),
                "llmTurnIndex", continuation.llmTurnIndex(),
                "iterationIndex", continuation.iterationIndex(),
                "nextToolCallIndex", continuation.nextToolCallIndex()
        ));
    }

    private String json(Object value) {
        try {
            return jsonCodec.write(value);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to snapshot Agent LLM continuation", ex);
        }
    }

    private List<AgentLlmToolCallPayload> latestAssistantToolCalls(List<AgentLlmMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AgentLlmMessage message = messages.get(index);
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                return message.toolCalls();
            }
        }
        return List.of();
    }

    private int indexOfToolCall(List<AgentLlmToolCallPayload> toolCalls, String toolCallId) {
        for (int index = 0; index < toolCalls.size(); index++) {
            if (toolCalls.get(index).id().equals(toolCallId)) return index;
        }
        return -1;
    }

    private record Continuation(Integer llmTurnIndex, Integer iterationIndex,
                                LlmTokenUsage tokenUsage, String assistantText) {
        private Continuation {
            llmTurnIndex = llmTurnIndex == null ? INITIAL_TURN_INDEX : llmTurnIndex;
            iterationIndex = iterationIndex == null ? 0 : iterationIndex;
            tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
            assistantText = assistantText == null ? "" : assistantText;
        }
    }

    private String resolveToolName(String toolCode) {
        try {
            return toolDefinitionSource.getRequired(toolCode).presentation().displayName();
        } catch (Exception ex) {
            return toolCode;
        }
    }

    private String clipText(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private Map<String, Object> eventPayload(Object... entries) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            payload.put((String) entries[i], entries[i + 1]);
        }
        return payload;
    }

    private AgentEvent publishBoundary(Long runId, String eventType, Map<String, Object> payload) {
        AgentEvent event = eventPublisher.publish(runId, eventType, payload);
        checkpointBoundary.checkpoint(event);
        return event;
    }
}
