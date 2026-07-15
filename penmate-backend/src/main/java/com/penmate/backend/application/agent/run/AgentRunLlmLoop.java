package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentLlmGateway llmGateway;
    private final AgentToolDefinitionSource toolDefinitionSource;
    private final AgentRunEventPublisher eventPublisher;
    private final ToolCallApplicationService toolCallService;

    public AgentRunLlmLoop(AgentLlmGateway llmGateway,
                           AgentToolDefinitionSource toolDefinitionSource,
                           AgentRunEventPublisher eventPublisher,
                           @Lazy ToolCallApplicationService toolCallService) {
        this.llmGateway = llmGateway;
        this.toolDefinitionSource = toolDefinitionSource;
        this.eventPublisher = eventPublisher;
        this.toolCallService = toolCallService;
    }

    public AgentRunLoopResult execute(AgentRunLoopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return executeFrom(request, new ArrayList<>(request.messages()), INITIAL_TURN_INDEX,
                LlmTokenUsage.ZERO, new StringBuilder());
    }

    public AgentRunLoopResult resumeApproved(AgentRunLoopRequest request, AgentRunPendingApproval pending) {
        Objects.requireNonNull(pending, "pending must not be null");
        try {
            List<AgentLlmMessage> messages = OBJECT_MAPPER.readValue(
                    pending.resumePayloadJson(), new TypeReference<List<AgentLlmMessage>>() { });
            Continuation continuation = OBJECT_MAPPER.readValue(pending.toolContextJson(), Continuation.class);
            ToolCallResult result = toolCallService.executeToolCall(new ToolCallRequest(
                    pending.projectId(), pending.runId(), pending.sessionId(), pending.turnId(), pending.toolCode(),
                    pending.toolArgsJson(),
                    pending.operatorId() == null ? request.operatorId() : pending.operatorId(),
                    request.traceId(), pending.toolContextJson(),
                    pending.idempotencyKey(), continuation.llmTurnIndex(), pending.toolCallId(), null,
                    pending.resumePayloadJson(), "APPROVED", pending.approvalId().toString()
            ));
            if (result == null || !"SUCCESS".equals(result.status())) {
                String message = result == null ? "Approved tool call returned no result" : result.errorMessage();
                return new AgentRunLoopResult(AgentRunLoopResult.Status.FAILED, message,
                        continuation.tokenUsage(), pending.approvalId());
            }
            eventPublisher.publish(request.runId(), "tool.call.completed", eventPayload(
                    "llmTurnIndex", continuation.llmTurnIndex(), "toolCallId", pending.toolCallId(),
                    "toolCode", pending.toolCode(), "outputPreview", clipText(result.toolOutput(), 200)
            ));
            messages.add(AgentLlmMessage.tool(pending.toolCallId(), result.toolOutput()));
            List<AgentLlmToolCallPayload> siblingCalls = latestAssistantToolCalls(messages);
            int approvedIndex = indexOfToolCall(siblingCalls, pending.toolCallId());
            if (approvedIndex < 0) {
                throw new IllegalStateException("Approved tool call is missing from the saved assistant response");
            }
            for (int index = approvedIndex + 1; index < siblingCalls.size(); index++) {
                AgentLlmToolCallPayload sibling = siblingCalls.get(index);
                String toolName = resolveToolName(sibling.functionName());
                eventPublisher.publish(request.runId(), "tool.call.started", eventPayload(
                        "llmTurnIndex", continuation.llmTurnIndex(),
                        "toolCallId", sibling.id(),
                        "toolCode", sibling.functionName(),
                        "toolName", toolName,
                        "resumedSibling", true,
                        "argumentsPreview", sibling.argumentsJson()
                ));
                ToolCallResult siblingResult = toolCallService.executeToolCall(new ToolCallRequest(
                        request.projectId(), request.runId(), request.sessionId(), request.turnId(),
                        sibling.functionName(), sibling.argumentsJson(), request.operatorId(), request.traceId(),
                        json(continuation), request.runId() + ":" + sibling.id(), continuation.llmTurnIndex(),
                        sibling.id(), json(siblingCalls), json(messages), null, null
                ));
                if (siblingResult == null) {
                    siblingResult = ToolCallResult.failed("TOOL_CALL_FAILED", "Tool call returned no result");
                }
                if ("WAITING_APPROVAL".equals(siblingResult.status())) {
                    eventPublisher.publish(request.runId(), "tool.call.waiting_approval", eventPayload(
                            "llmTurnIndex", continuation.llmTurnIndex(),
                            "toolCallId", sibling.id(),
                            "toolCode", sibling.functionName(),
                            "approvalId", siblingResult.approvalId()
                    ));
                    return AgentRunLoopResult.waitingApproval(
                            siblingResult.approvalId(), continuation.assistantText(), continuation.tokenUsage());
                }
                if ("SUCCESS".equals(siblingResult.status())) {
                    eventPublisher.publish(request.runId(), "tool.call.completed", eventPayload(
                            "llmTurnIndex", continuation.llmTurnIndex(),
                            "toolCallId", sibling.id(),
                            "toolCode", sibling.functionName(),
                            "outputPreview", clipText(siblingResult.toolOutput(), 200)
                    ));
                    messages.add(AgentLlmMessage.tool(sibling.id(), siblingResult.toolOutput()));
                } else {
                    String error = siblingResult.errorMessage() == null ? "Unknown error" : siblingResult.errorMessage();
                    eventPublisher.publish(request.runId(), "tool.call.failed", eventPayload(
                            "llmTurnIndex", continuation.llmTurnIndex(),
                            "toolCallId", sibling.id(),
                            "toolCode", sibling.functionName(),
                            "errorCode", siblingResult.errorCode(),
                            "errorMessage", error
                    ));
                    messages.add(AgentLlmMessage.tool(sibling.id(), "Error: " + error));
                }
            }
            return executeFrom(request, messages, continuation.llmTurnIndex() + 1,
                    continuation.tokenUsage(), new StringBuilder(continuation.assistantText()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to restore approved Agent tool continuation", ex);
        }
    }

    private AgentRunLoopResult executeFrom(AgentRunLoopRequest request, List<AgentLlmMessage> messages,
                                           int initialTurnIndex, LlmTokenUsage initialUsage,
                                           StringBuilder fullAssistantText) {
        int turnIndex = initialTurnIndex;
        LlmTokenUsage totalUsage = initialUsage;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            eventPublisher.publish(request.runId(), "llm.turn.started", eventPayload(
                    "llmTurnIndex", turnIndex,
                    "traceId", request.traceId()
            ));

            AgentLlmTurnResponse response = llmGateway.generateTurn(
                    new AgentLlmTurnRequest(
                            List.copyOf(messages),
                            toolDefinitionSource.listLlmSchemas(),
                            "auto"
                    ),
                    request.executionConfig()
            );

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

            if (!response.assistantText().isBlank()) {
                eventPublisher.broadcastOnly(request.runId(), "message.delta",
                        Map.of("llmTurnIndex", turnIndex, "text", response.assistantText()), -1L);
            }

            if (response.toolCalls().isEmpty()) {
                return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
            }

            List<AgentLlmToolCallPayload> toolCallPayloads = response.toolCalls().stream()
                    .map(tc -> new AgentLlmToolCallPayload(tc.id(), "function", tc.toolCode(), tc.argumentsJson()))
                    .toList();
            messages.add(AgentLlmMessage.assistant(response.assistantText(), toolCallPayloads));

            for (AgentLlmToolCall toolCall : response.toolCalls()) {
                String toolName = resolveToolName(toolCall.toolCode());

                eventPublisher.publish(request.runId(), "tool.call.started", eventPayload(
                        "llmTurnIndex", turnIndex,
                        "toolCallId", toolCall.id(),
                        "toolCode", toolCall.toolCode(),
                        "toolName", toolName,
                        "iteration", iteration,
                        "argumentsPreview", toolCall.argumentsJson()
                ));

                ToolCallResult result = toolCallService.executeToolCall(new ToolCallRequest(
                        request.projectId(),
                        request.runId(),
                        request.sessionId(),
                        request.turnId(),
                        toolCall.toolCode(),
                        toolCall.argumentsJson(),
                        request.operatorId(),
                        request.traceId(),
                        json(new Continuation(turnIndex, totalUsage, fullAssistantText.toString())),
                        request.runId() + ":" + toolCall.id(),
                        turnIndex,
                        toolCall.id(),
                        json(toolCallPayloads),
                        json(messages),
                        null,
                        null
                ));
                if (result == null) {
                    result = ToolCallResult.failed("TOOL_CALL_FAILED", "Tool call returned no result");
                }

                if ("WAITING_APPROVAL".equals(result.status())) {
                    eventPublisher.publish(request.runId(), "tool.call.waiting_approval", eventPayload(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.toolCode(),
                            "approvalId", result.approvalId()
                    ));
                    log.info("Tool call waiting approval: runId={}, toolCode={}, approvalId={}",
                            request.runId(), toolCall.toolCode(), result.approvalId());
                    return AgentRunLoopResult.waitingApproval(
                            result.approvalId(),
                            fullAssistantText.toString(),
                            totalUsage
                    );
                }

                if ("SUCCESS".equals(result.status())) {
                    eventPublisher.publish(request.runId(), "tool.call.completed", eventPayload(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.toolCode(),
                            "outputPreview", clipText(result.toolOutput(), 200)
                    ));
                    messages.add(AgentLlmMessage.tool(toolCall.id(), result.toolOutput()));
                } else {
                    String errorOutput = "Error: " + result.errorMessage();
                    eventPublisher.publish(request.runId(), "tool.call.failed", eventPayload(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.toolCode(),
                            "errorCode", result.errorCode(),
                            "errorMessage", result.errorMessage()
                    ));
                    messages.add(AgentLlmMessage.tool(toolCall.id(), errorOutput));
                }
            }

            turnIndex++;
        }

        return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
    }

    private String json(Object value) {
        try { return OBJECT_MAPPER.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Failed to snapshot Agent LLM continuation", ex); }
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

    private record Continuation(Integer llmTurnIndex, LlmTokenUsage tokenUsage, String assistantText) {
        private Continuation {
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
}
