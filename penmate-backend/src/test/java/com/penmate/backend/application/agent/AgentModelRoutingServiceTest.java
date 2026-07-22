package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        ModelConfiguration model = new ModelConfiguration();
        model.setModelConfigId(9001L);
        model.setModelType("CHAT");
        model.setStatus("ACTIVE");
        model.setProviderCode("openai");
        model.setProviderAuthType("API_KEY");
        model.setModelName("gpt-4o-mini");
        model.setBaseUrl("https://api.openai.com/v1");
        model.setContextWindowTurns(8);
        ModelCredential credential = new ModelCredential();
        credential.setModelConfigId(9001L);
        credential.setEncryptedApiKey("cipher-key");
        credential.setStatus("ACTIVE");
        when(modelRepository.findAccessibleConfiguration(1001L, 9001L)).thenReturn(model);
        when(modelRepository.findCredential(model)).thenReturn(credential);
        when(secretCryptoService.decrypt("cipher-key")).thenReturn("sk-live");

        AgentLlmExecutionConfig config = agentModelRoutingService.resolveExecutionConfig(1001L, 9001L, "trace-ctx-window");

        assertThat(config.contextWindowTurns()).isEqualTo(8);
    }

    @Test
    void should_use_default_creative_model_when_tool_does_not_override_model() {
        ModelUserPreferences preferences = new ModelUserPreferences();
        preferences.setUserId(1001L);
        preferences.setDefaultCreativeModelConfigId(9001L);
        ModelConfiguration model = new ModelConfiguration();
        model.setModelConfigId(9001L);
        model.setModelType("CHAT");
        model.setStatus("ACTIVE");
        model.setProviderCode("local");
        model.setProviderAuthType("NONE");
        model.setModelName("creative-model");
        when(modelRepository.findUserPreferences(1001L)).thenReturn(preferences);
        when(modelRepository.findAccessibleConfiguration(1001L, 9001L)).thenReturn(model);

        AgentLlmExecutionConfig config = agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-default");

        assertThat(config.modelConfigId()).isEqualTo(9001L);
        assertThat(config.modelName()).isEqualTo("creative-model");
    }
}
