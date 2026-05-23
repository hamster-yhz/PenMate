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
    void should_carry_context_window_turns_into_execution_config() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "https://api.openai.com/v1",
                "encryptedApiKey", "cipher-key",
                "keyStatus", "active",
                "contextWindowTurns", 8
        ));
        when(secretCryptoService.decrypt("cipher-key")).thenReturn("sk-live");

        AgentLlmExecutionConfig config = agentModelRoutingService.resolveExecutionConfig(1001L, 9001L, "trace-ctx-window");

        assertThat(config.contextWindowTurns()).isEqualTo(8);
    }
}
