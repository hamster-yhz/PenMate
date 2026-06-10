package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
public class AgentRunLlmLoop {

    private static final int INITIAL_TURN_INDEX = 1;

    private final AgentLlmGateway llmGateway;
    private final AgentToolDefinitionSource toolDefinitionSource;
    private final AgentRunEventPublisher eventPublisher;

    public AgentRunLlmLoop(AgentLlmGateway llmGateway,
                           AgentToolDefinitionSource toolDefinitionSource,
                           AgentRunEventPublisher eventPublisher) {
        this.llmGateway = llmGateway;
        this.toolDefinitionSource = toolDefinitionSource;
        this.eventPublisher = eventPublisher;
    }

    public AgentRunLoopResult execute(AgentRunLoopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        eventPublisher.publish(request.runId(), "llm.turn.started", Map.of(
                "llmTurnIndex", INITIAL_TURN_INDEX,
                "traceId", request.traceId()
        ));

        AgentLlmTurnResponse response = llmGateway.generateTurn(
                new AgentLlmTurnRequest(request.messages(), toolDefinitionSource.listLlmSchemas(), "auto"),
                request.executionConfig()
        );

        eventPublisher.publish(request.runId(), "llm.turn.completed", Map.of(
                "llmTurnIndex", INITIAL_TURN_INDEX,
                "finishReason", response.finishReason(),
                "toolCallCount", response.toolCalls().size()
        ));

        String assistantText = response.assistantText();
        if (!assistantText.isBlank()) {
            eventPublisher.publish(request.runId(), "message.delta", Map.of(
                    "llmTurnIndex", INITIAL_TURN_INDEX,
                    "text", assistantText
            ));
        }

        return AgentRunLoopResult.completed(assistantText, response.tokenUsage());
    }
}
