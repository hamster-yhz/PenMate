package com.penmate.backend.application.agent.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentQueuedRequest;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentQueuedRequestRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentQueuedRequestApplicationServiceTest {
    private final AgentQueuedRequestRepository requests = mock(AgentQueuedRequestRepository.class);
    private final AgentSessionRepository sessions = mock(AgentSessionRepository.class);
    private final BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
    private final AgentQueuedRequestApplicationService service = new AgentQueuedRequestApplicationService(
            requests, sessions, ids, new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void registers_one_persistent_message_request() {
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Session"));
        when(ids.nextId()).thenReturn(40L);
        when(requests.insert(any())).thenReturn(1);
        var payload = new AgentQueuedRequestApplicationService.QueueMessagePayload(
                "continue", List.of("canon-maintenance"),
                new AgentQueuedRequestApplicationService.TaskRequest(50L, java.util.List.of(50L), 60L, null));

        AgentQueuedRequest registered = service.register(10L, 20L, 30L, "message", payload);

        assertThat(registered.requestId()).isEqualTo(40L);
        assertThat(registered.requestType()).isEqualTo("MESSAGE");
        assertThat(registered.payloadJson()).contains("continue", "canon-maintenance");
        verify(requests).insert(registered);
    }

    @Test
    void returns_the_fixed_conflict_when_an_open_request_already_exists() {
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Session"));
        when(requests.findOpen(10L, 20L)).thenReturn(request("PENDING", 0));

        assertThatThrownBy(() -> service.register(10L, 20L, 30L, "COMPRESS", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AgentQueuedRequestApplicationService.OPEN_REQUEST_MESSAGE);

        verify(requests, never()).insert(any());
    }

    @Test
    void maps_the_atomic_repository_insert_conflict_to_the_same_business_error() {
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Session"));
        when(ids.nextId()).thenReturn(40L);
        when(requests.insert(any())).thenReturn(0);

        assertThatThrownBy(() -> service.register(10L, 20L, 30L, "COMPRESS", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先撤回当前待执行请求");
    }

    @Test
    void only_withdraws_a_pending_request_owned_by_the_session_user() {
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Session"));
        when(requests.withdraw(10L, 20L, 40L, 30L)).thenReturn(1);

        service.withdraw(10L, 20L, 40L, 30L);

        verify(requests).withdraw(10L, 20L, 40L, 30L);
    }

    private AgentQueuedRequest request(String status, int attempts) {
        return new AgentQueuedRequest(40L, 10L, 20L, 30L, "COMPRESS", null,
                status, attempts, null, null, null);
    }
}
