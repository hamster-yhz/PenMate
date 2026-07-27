package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmMessageRole;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

final class ProviderPromptCacheSupport {

    private ProviderPromptCacheSupport() {
    }

    static String cacheKey(AgentLlmTurnRequest request, AgentLlmExecutionConfig config) {
        AgentLlmMessage stablePrefix = firstStableSystemMessage(request);
        if (stablePrefix == null || stablePrefix.content().isBlank() || config == null) return null;

        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        identity.put("model", normalize(config.modelName()));
        identity.put("stablePrefix", stablePrefix.content());
        identity.put("tools", (request.tools() == null ? List.<AgentLlmToolSchema>of() : request.tools()).stream()
                .sorted(Comparator.comparing(schema -> normalize(schema.toolCode())))
                .map(schema -> List.of(normalize(schema.toolCode()), normalize(schema.description()),
                        normalize(schema.parametersJsonSchema())))
                .toList());
        return sha256(AgentJsonCodec.toJson(identity));
    }

    static AgentLlmMessage firstStableSystemMessage(AgentLlmTurnRequest request) {
        if (request == null || request.messages() == null) return null;
        return request.messages().stream()
                .filter(message -> message != null && message.role() == AgentLlmMessageRole.SYSTEM)
                .findFirst()
                .orElse(null);
    }

    static boolean useExplicitOpenAiBreakpoint(AgentLlmExecutionConfig config) {
        return config != null && normalize(config.modelName()).toLowerCase(java.util.Locale.ROOT)
                .startsWith("gpt-5.6");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
