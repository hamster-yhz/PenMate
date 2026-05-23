package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.query.AgentSessionTokenUsageQueryService;
import com.penmate.backend.application.agent.runtime.SessionTokenUsageView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSessionTokenUsageAppServiceTest {

    @Mock
    private AgentSessionTokenUsageQueryService agentSessionTokenUsageQueryService;

    @InjectMocks
    private AgentSessionTokenUsageAppService agentSessionTokenUsageAppService;

    @Test
    void should_delegate_to_query_service_and_return_token_usage_view() {
        SessionTokenUsageView expected = new SessionTokenUsageView(64000, 128000, 0.5d, 48000, 16000, "gpt-4.1");
        when(agentSessionTokenUsageQueryService.getTokenUsage(101L, 90001L, "trace-token-usage-app-1"))
                .thenReturn(expected);

        SessionTokenUsageView actual = agentSessionTokenUsageAppService.getTokenUsage(101L, 90001L, "trace-token-usage-app-1");

        assertThat(actual).isEqualTo(expected);
        verify(agentSessionTokenUsageQueryService).getTokenUsage(101L, 90001L, "trace-token-usage-app-1");
    }
}
