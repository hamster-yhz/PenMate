package com.penmate.backend.domain.agent.model;

import java.util.Objects;

public record AgentLlmProviderItem(String protocolCode, String payloadJson) {

    public AgentLlmProviderItem {
        protocolCode = Objects.requireNonNull(protocolCode, "protocolCode must not be null").trim();
        payloadJson = Objects.requireNonNull(payloadJson, "payloadJson must not be null").trim();
        if (protocolCode.isEmpty() || payloadJson.isEmpty()) {
            throw new IllegalArgumentException("Provider item protocol and payload must not be blank");
        }
    }
}
