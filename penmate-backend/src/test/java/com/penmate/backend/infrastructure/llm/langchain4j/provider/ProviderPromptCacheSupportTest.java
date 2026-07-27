package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPromptCacheSupportTest {

    private final AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder()
            .modelName("gpt-5.6-sol")
            .build();
    private final AgentLlmToolSchema tool = new AgentLlmToolSchema(
            "story_search", "Search", "{\"type\":\"object\"}");

    @Test
    void cache_key_is_stable_when_the_conversation_grows() {
        AgentLlmTurnRequest first = request(List.of(
                AgentLlmMessage.system("stable"),
                AgentLlmMessage.system("dynamic"),
                AgentLlmMessage.user("one")));
        AgentLlmTurnRequest next = request(List.of(
                AgentLlmMessage.system("stable"),
                AgentLlmMessage.system("dynamic"),
                AgentLlmMessage.user("one"),
                AgentLlmMessage.assistant("working", List.of()),
                AgentLlmMessage.user("two")));

        assertThat(ProviderPromptCacheSupport.cacheKey(next, config))
                .isEqualTo(ProviderPromptCacheSupport.cacheKey(first, config))
                .hasSize(64);
    }

    @Test
    void cache_key_changes_with_the_stable_prefix_or_tools() {
        String baseline = ProviderPromptCacheSupport.cacheKey(
                request(List.of(AgentLlmMessage.system("stable"), AgentLlmMessage.user("one"))), config);
        String changedPrefix = ProviderPromptCacheSupport.cacheKey(
                request(List.of(AgentLlmMessage.system("stable-v2"), AgentLlmMessage.user("one"))), config);
        AgentLlmTurnRequest changedTools = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.system("stable"), AgentLlmMessage.user("one")),
                List.of(new AgentLlmToolSchema("other", "Other", "{\"type\":\"object\"}")), "auto");

        assertThat(changedPrefix).isNotEqualTo(baseline);
        assertThat(ProviderPromptCacheSupport.cacheKey(changedTools, config)).isNotEqualTo(baseline);
    }

    private AgentLlmTurnRequest request(List<AgentLlmMessage> messages) {
        return new AgentLlmTurnRequest(messages, List.of(tool), "auto");
    }
}
