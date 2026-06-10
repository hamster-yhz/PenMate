package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.context.AgentContextRoutingFacade;
import com.penmate.backend.application.agent.context.AgentContextRoutingResult;
import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.context.StoryBibleContextResult;
import com.penmate.backend.application.agent.llm.LlmTokenUsage;
import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightCoordinator;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunExecutorTest {

    @Mock
    private AgentRunRepository runRepository;
    @Mock
    private AgentRunEventPublisher eventPublisher;
    @Mock
    private AgentPreflightCoordinator preflightCoordinator;
    @Mock
    private AgentContextRoutingFacade contextRoutingFacade;
    @Mock
    private PromptComposer promptComposer;
    @Mock
    private AgentRunLlmLoop llmLoop;

    @Test
    void executor_runs_preflight_context_prompt_llm_and_completion_events() {
        when(runRepository.findInput(70001L)).thenReturn(runInput());
        when(preflightCoordinator.coordinate(any())).thenReturn(preflightDecision());
        when(contextRoutingFacade.route(any())).thenReturn(contextRoutingResult());
        when(promptComposer.compose(any(), any(), eq("Write a suspense opening."))).thenReturn(promptPlan());
        when(llmLoop.execute(any())).thenReturn(AgentRunLoopResult.completed("完成文本", new LlmTokenUsage(10, 5, 15)));
        AgentRunExecutor executor = new AgentRunExecutor(
                runRepository,
                eventPublisher,
                preflightCoordinator,
                contextRoutingFacade,
                promptComposer,
                llmLoop
        );

        executor.execute(70001L, "trace-1");

        verify(eventPublisher).publish(eq(70001L), eq("run.phase.changed"), containsText("preflight"));
        verify(eventPublisher).publish(eq(70001L), eq("run.phase.changed"), containsText("context"));
        verify(eventPublisher).publish(eq(70001L), eq("context.routing.completed"), any());
        verify(eventPublisher).publish(eq(70001L), eq("run.phase.changed"), containsText("prompt"));
        verify(eventPublisher).publish(eq(70001L), eq("run.phase.changed"), containsText("executing"));
        verify(eventPublisher).publish(eq(70001L), eq("message.completed"), containsText("完成文本"));
        verify(eventPublisher).publish(eq(70001L), eq("run.completed"), any());
    }

    private AgentRunInput runInput() {
        return new AgentRunInput(
                70001L,
                "Write a suspense opening.",
                "WRITE",
                30001L,
                "selected text",
                "{\"styleId\":81}",
                "{\"modelConfigId\":1001}",
                null,
                "hash-70001"
        );
    }

    private AgentPreflightDecision preflightDecision() {
        return new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                true,
                true,
                false,
                "write opening",
                "{}",
                List.of("WRITE"),
                List.of(),
                List.of(),
                List.of(),
                "draft",
                false,
                false,
                false
        );
    }

    private AgentContextRoutingResult contextRoutingResult() {
        return new AgentContextRoutingResult(
                "{\"styleId\":81}",
                StoryBibleContextResult.noop(),
                new ContextPackage(List.of("style"), List.of(), List.of(), List.of(), List.of("rag-1"), "{\"styleId\":81}", "chapter:30001")
        );
    }

    private PromptPlan promptPlan() {
        return new PromptPlan(List.of(), List.of(), "default", "assembled prompt");
    }

    private Object containsText(String expected) {
        return argThat((ArgumentMatcher<Object>) payload -> payload != null && payload.toString().contains(expected));
    }
}
