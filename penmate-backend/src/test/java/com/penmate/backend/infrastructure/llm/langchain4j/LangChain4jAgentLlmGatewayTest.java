package com.penmate.backend.infrastructure.llm.langchain4j;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClient;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClientFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangChain4jAgentLlmGatewayTest {

    @Test
    void UT_INFRA_LLM_LANGCHAIN4J_AGENT_LLM_GATEWAY_DELEGATES_GENERATE_TURN_TO_PROVIDER_AND_RETURNS_STRUCTURED_RESPONSE() {
        ProviderChatClientFactory factory = mock(ProviderChatClientFactory.class);
        ProviderChatClient providerChatClient = mock(ProviderChatClient.class);
        LangChain4jAgentLlmGateway gateway = new LangChain4jAgentLlmGateway(factory);
        AgentLlmExecutionConfig executionConfig = new AgentLlmExecutionConfig(
                1L,
                "openai-compatible",
                "https://example.com/v1",
                "sk-test",
                "gpt-test",
                "USER_KEY"
        );
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                "auto"
        );
        AgentLlmTurnResponse expected = new AgentLlmTurnResponse(
                "tool_calls",
                "",
                List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"hello\"}")),
                "{}"
        );

        when(factory.get("openai-compatible")).thenReturn(providerChatClient);
        when(providerChatClient.generateTurn(same(request), same(executionConfig))).thenReturn(expected);

        AgentLlmTurnResponse actual = gateway.generateTurn(request, executionConfig);

        assertThat(actual).isEqualTo(expected);
        verify(factory).get("openai-compatible");
        verify(providerChatClient).generateTurn(same(request), same(executionConfig));
    }

    @Test
    void should_build_structured_prompt_without_legacy_headings_when_generate_is_used() {
        ProviderChatClientFactory factory = mock(ProviderChatClientFactory.class);
        ProviderChatClient providerChatClient = mock(ProviderChatClient.class);
        LangChain4jAgentLlmGateway gateway = new LangChain4jAgentLlmGateway(factory);
        AgentLlmExecutionConfig executionConfig = new AgentLlmExecutionConfig(
                1L,
                "openai-compatible",
                "https://example.com/v1",
                "sk-test",
                "gpt-test",
                "USER_KEY"
        );
        AgentGenerationTask task = new AgentGenerationTask();
        task.setPromptSnapshot("请整理设定冲突");

        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setDocumentTitle("设定集");
        chunk.setChunkNo(2);
        chunk.setContentText("边境要塞三日一换防。 ");

        when(factory.get("openai-compatible")).thenReturn(providerChatClient);
        when(providerChatClient.generate(org.mockito.ArgumentMatchers.anyString(), same(executionConfig))).thenReturn("ok");

        String actual = gateway.generate(task, List.of(chunk), "工具返回：检索到边防档案", executionConfig);

        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(providerChatClient).generate(promptCaptor.capture(), same(executionConfig));
        assertThat(actual).isEqualTo("ok");
        assertThat(promptCaptor.getValue())
                .contains("<context type=\"rag\">\n- [设定集#2] 边境要塞三日一换防。 \n</context>")
                .contains("<context type=\"tool\">\n工具返回：检索到边防档案\n</context>")
                .contains("<user_request>\n请整理设定冲突\n</user_request>")
                .doesNotContain("知识库参考：")
                .doesNotContain("工具增强结果：")
                .doesNotContain("用户指令：");
    }
}
