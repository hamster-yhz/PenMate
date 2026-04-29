package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingToolInvocationRepositoryImplTest {

    @Mock
    private PendingToolInvocationMapper pendingToolInvocationMapper;

    @InjectMocks
    private PendingToolInvocationRepositoryImpl repository;

    @Test
    void UT_INFRA_AGENT_PENDING_TOOL_INVOCATION_REPOSITORY_SAVE_AND_FIND_BY_APPROVAL_ID() {
        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                88001L,
                10001L,
                8001L,
                7001L,
                "book_crud",
                "{\"operation\":\"delete\"}",
                "{}",
                1001L,
                "trace-approval-1",
                "book-crud-delete-8001",
                "pending"
        );
        when(pendingToolInvocationMapper.findByApprovalId(88001L)).thenReturn(snapshot);

        repository.save(snapshot);
        PendingToolInvocationSnapshot loaded = repository.findByApprovalId(88001L);

        verify(pendingToolInvocationMapper).insert(snapshot);
        verify(pendingToolInvocationMapper).findByApprovalId(88001L);
        assertThat(loaded).isEqualTo(snapshot);
    }
}
