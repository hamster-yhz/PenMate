package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.prompt.PromptContextRenderer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.application.agent.prompt.StructuredPromptBlockFormatter;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmMessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPromptAssemblerTest {

    private final AgentPromptAssembler assembler = new AgentPromptAssembler(
            new PromptContextRenderer(new StructuredPromptBlockFormatter()));

    @Test
    void should_render_stable_dynamic_history_and_user_messages_in_contract_order() {
        PromptPlan plan = new PromptPlan(List.of(), List.of(),
                "stable instructions", "<context type=\"rag\">\nreference\n</context>", "preview");
        List<AgentLlmMessage> history = List.of(
                AgentLlmMessage.user("earlier request"),
                AgentLlmMessage.assistant("earlier answer", List.of()));

        List<AgentLlmMessage> messages = assembler.buildExecutionMessages(
                plan, "Continue <now>", history);

        assertThat(messages).extracting(AgentLlmMessage::role).containsExactly(
                AgentLlmMessageRole.SYSTEM,
                AgentLlmMessageRole.SYSTEM,
                AgentLlmMessageRole.USER,
                AgentLlmMessageRole.ASSISTANT,
                AgentLlmMessageRole.USER);
        assertThat(messages).extracting(AgentLlmMessage::content).containsExactly(
                "stable instructions",
                "<context type=\"rag\">\nreference\n</context>",
                "earlier request",
                "earlier answer",
                "<user_request>\nContinue &lt;now&gt;\n</user_request>");
    }

    @Test
    void should_insert_activated_skills_between_stable_prefix_and_dynamic_context() {
        PromptPlan plan = new PromptPlan(List.of(), List.of(),
                "stable", "dynamic", "preview");

        List<AgentLlmMessage> messages = assembler.buildExecutionMessages(
                plan, "activated writer", "continue", List.of(AgentLlmMessage.user("history")));

        assertThat(messages).extracting(AgentLlmMessage::content).containsExactly(
                "stable",
                "activated writer",
                "dynamic",
                "history",
                "<user_request>\ncontinue\n</user_request>");
    }
}
