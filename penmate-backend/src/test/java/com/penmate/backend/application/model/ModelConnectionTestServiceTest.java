package com.penmate.backend.application.model;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.repository.ModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelConnectionTestServiceTest {
    @Mock private ModelRepository repository;
    @Mock private ModelApplicationService models;
    @Mock private AgentModelRoutingService routing;
    @Mock private AgentLlmGateway llm;
    @InjectMocks private ModelConnectionTestService service;

    @Test
    void tests_a_saved_chat_configuration_with_a_minimal_turn_and_records_success() {
        ModelConfiguration configuration = configuration("USER", "CHAT");
        when(repository.findAccessibleConfiguration(7L, 101L)).thenReturn(configuration);
        AgentLlmExecutionConfig execution = AgentLlmExecutionConfig.builder()
                .modelConfigId(101L).providerCode("openai-compatible")
                .baseUrl("https://example.test/v1").apiKey("secret").modelName("chat-1").build();
        when(routing.resolveExecutionConfig(7L, 101L, "trace-1")).thenReturn(execution);
        when(llm.generateTurn(any(), eq(execution)))
                .thenReturn(new AgentLlmTurnResponse("stop", "OK", List.of(), "{}"));

        ModelConnectionTestService.ConnectionTestResult result = service.test(7L, 101L, false, "trace-1");

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        verify(repository).updateConnectionTest(eq(7L), eq(101L), eq(false), eq("SUCCESS"),
                anyInt(), isNull(), any(Instant.class));
    }

    @Test
    void records_a_sanitized_failure_instead_of_returning_a_secret() {
        when(repository.findAccessibleConfiguration(7L, 101L)).thenReturn(configuration("USER", "CHAT"));
        when(routing.resolveExecutionConfig(7L, 101L, "trace-2"))
                .thenThrow(BusinessException.of("Bearer sk-super-secret rejected"));

        ModelConnectionTestService.ConnectionTestResult result = service.test(7L, 101L, false, "trace-2");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).doesNotContain("super-secret").contains("****");
        verify(repository).updateConnectionTest(eq(7L), eq(101L), eq(false), eq("FAILED"),
                anyInt(), eq(result.error()), any(Instant.class));
    }

    private ModelConfiguration configuration(String scope, String type) {
        ModelConfiguration configuration = new ModelConfiguration();
        configuration.setModelConfigId(101L);
        configuration.setScopeType(scope);
        configuration.setModelType(type);
        configuration.setStatus("ACTIVE");
        return configuration;
    }
}
