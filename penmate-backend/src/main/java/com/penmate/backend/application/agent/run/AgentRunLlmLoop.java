package com.penmate.backend.application.agent.run;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AgentRunLlmLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentRunLlmLoop.class);
    private static final int INITIAL_TURN_INDEX = 1;
    private static final int MAX_ITERATIONS = 10;

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

        List<AgentLlmMessage> messages = new ArrayList<>(request.messages());
        int turnIndex = INITIAL_TURN_INDEX;
        LlmTokenUsage totalUsage = LlmTokenUsage.ZERO;
        StringBuilder fullAssistantText = new StringBuilder();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            eventPublisher.publish(request.runId(), "llm.turn.started", Map.of(
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
                            "totalTokens", response.tokenUsage().totalTokens()
                    )
            ));

            if (!response.assistantText().isBlank()) {
                eventPublisher.broadcastOnly(request.runId(), "message.delta",
                        Map.of("llmTurnIndex", turnIndex, "text", response.assistantText()), -1L);
            }

            if (response.toolCalls().isEmpty()) {
                eventPublisher.publish(request.runId(), "message.completed", Map.of(
                        "llmTurnIndex", turnIndex,
                        "role", "assistant",
                        "text", fullAssistantText.toString()
                ));
                return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
            }

            for (AgentLlmToolCall toolCall : response.toolCalls()) {
                String toolName = resolveToolName(toolCall.toolCode());

                eventPublisher.publish(request.runId(), "tool.call.started", Map.of(
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
                        null,
                        request.traceId(),
                        null,
                        toolCall.id(),
                        turnIndex,
                        toolCall.id(),
                        null,
                        null,
                        null,
                        null
                ));

                if ("WAITING_APPROVAL".equals(result.status())) {
                    eventPublisher.publish(request.runId(), "tool.call.waiting_approval", Map.of(
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
                    eventPublisher.publish(request.runId(), "tool.call.completed", Map.of(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.toolCode(),
                            "outputPreview", clipText(result.toolOutput(), 200)
                    ));
                    messages.add(AgentLlmMessage.tool(toolCall.id(), result.toolOutput()));
                } else {
                    String errorOutput = "Error: " + (result.errorMessage() != null ? result.errorMessage() : "Unknown error");
                    eventPublisher.publish(request.runId(), "tool.call.failed", Map.of(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.toolCode(),
                            "errorCode", result.errorCode(),
                            "errorMessage", result.errorMessage()
                    ));
                    messages.add(AgentLlmMessage.tool(toolCall.id(), errorOutput));
                }
            }

            List<AgentLlmToolCallPayload> toolCallPayloads = response.toolCalls().stream()
                    .map(tc -> new AgentLlmToolCallPayload(tc.id(), "function", tc.toolCode(), tc.argumentsJson()))
                    .toList();
            messages.add(AgentLlmMessage.assistant(fullAssistantText.toString(), toolCallPayloads));
            turnIndex++;
        }

        eventPublisher.publish(request.runId(), "message.completed", Map.of(
                "llmTurnIndex", turnIndex,
                "role", "assistant",
                "text", fullAssistantText.toString()
        ));
        return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
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
}