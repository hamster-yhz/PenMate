package com.penmate.backend.infrastructure.llm.langchain4j;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.prompt.StructuredPromptBlockFormatter;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClient;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LangChain4jAgentLlmGateway implements AgentLlmGateway {

    private final ProviderChatClientFactory providerChatClientFactory;

    public LangChain4jAgentLlmGateway(ProviderChatClientFactory providerChatClientFactory,
                                      StructuredPromptBlockFormatter ignoredFormatter) {
        this.providerChatClientFactory = providerChatClientFactory;
    }

    @Override
    public AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                             AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }
        String provider = executionConfig.providerCode();
        String baseUrl = executionConfig.baseUrl();
        String apiKey = executionConfig.apiKey();
        String modelName = executionConfig.modelName();
        if (provider == null || provider.isBlank() || baseUrl == null || baseUrl.isBlank()
                || apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            throw BusinessException.of("LLM execution config is incomplete");
        }
        log.info("agent.llm.gateway.resolved: runTurn provider={}, baseUrl={}, modelName={}, modelConfigId={}",
                provider,
                baseUrl,
                modelName,
                executionConfig.modelConfigId());
        ProviderChatClient providerChatClient = providerChatClientFactory.get(provider);
        return providerChatClient.generateTurn(request, executionConfig);
    }
}
