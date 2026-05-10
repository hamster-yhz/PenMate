package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentModelRoutingServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private SecretCryptoService secretCryptoService;

    @InjectMocks
    private AgentModelRoutingService agentModelRoutingService;

    @Test
    void UT_APP_AGENT_MODEL_ROUTING_SERVICE_RESOLVES_OPENAI_COMPATIBLE_PROVIDER_CODE_AND_PRESERVES_CUSTOM_BASE_URL_FROM_MODEL_CONFIG() {
        when(modelRepository.findUserModelConfig(1001L, 920025L)).thenReturn(Map.of(
                "modelName", "openai-compatible-chat",
                "providerId", 7L,
                "keyStatus", "active",
                "encryptedApiKey", "cipher-openai-compatible",
                "baseUrl", "https://gateway.internal/openai-compatible"
        ));
        when(secretCryptoService.decrypt("cipher-openai-compatible")).thenReturn("sk-openai-compatible");

        AgentLlmExecutionConfig actual = agentModelRoutingService.resolveExecutionConfig(1001L, 920025L, "trace-openai-compatible");

        assertThat(actual.providerCode()).isEqualTo("openai-compatible");
        assertThat(actual.baseUrl()).isEqualTo("https://gateway.internal/openai-compatible");
        assertThat(actual.apiKey()).isEqualTo("sk-openai-compatible");
        assertThat(actual.modelName()).isEqualTo("openai-compatible-chat");
        verify(modelRepository).findUserModelConfig(1001L, 920025L);
    }
}
