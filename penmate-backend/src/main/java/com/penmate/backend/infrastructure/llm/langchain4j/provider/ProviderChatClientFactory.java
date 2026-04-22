package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProviderChatClientFactory {

    private final List<ProviderChatClient> clients;

    public ProviderChatClientFactory(List<ProviderChatClient> clients) {
        this.clients = clients;
    }

    public ProviderChatClient get(String providerCode) {
        return clients.stream()
                .filter(client -> client.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> BusinessException.of("Unsupported provider: " + providerCode));
    }
}

