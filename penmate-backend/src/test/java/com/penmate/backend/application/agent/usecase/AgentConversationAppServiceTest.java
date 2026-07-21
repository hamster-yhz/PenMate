package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConversationAppServiceTest {

    @Mock private AgentRepository repository;
    @Mock private BusinessIdGenerator ids;
    private AgentConversationAppService service;

    @BeforeEach
    void setUp() {
        service = new AgentConversationAppService(repository, ids);
    }

    @Test
    void rename_trims_title_and_updates_only_owned_active_session() {
        when(repository.findConversation(10L, 20L)).thenReturn(conversation(30L, null));
        when(repository.updateConversationTitle(10L, 20L, 30L, "New title")).thenReturn(1);

        AgentConversation result = service.renameConversation(10L, 20L, 30L, "  New title  ");

        assertThat(result.getTitle()).isEqualTo("New title");
        verify(repository).updateConversationTitle(10L, 20L, 30L, "New title");
    }

    @Test
    void delete_rejects_session_with_active_run() {
        when(repository.findConversation(10L, 20L)).thenReturn(conversation(30L, null));
        when(repository.countActiveRuns(20L)).thenReturn(1);

        assertThatThrownBy(() -> service.deleteConversation(10L, 20L, 30L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stop the active run");
        verify(repository, never()).softDeleteConversation(10L, 20L, 30L);
    }

    @Test
    void restore_requires_a_deleted_owned_session() {
        AgentConversation deleted = conversation(30L, Instant.parse("2026-07-20T12:00:00Z"));
        when(repository.findConversationIncludingDeleted(10L, 20L)).thenReturn(deleted);
        when(repository.restoreConversation(10L, 20L, 30L)).thenReturn(1);

        AgentConversation result = service.restoreConversation(10L, 20L, 30L);

        assertThat(result.getDeletedAt()).isNull();
        verify(repository).restoreConversation(10L, 20L, 30L);
    }

    @Test
    void ownership_mismatch_is_forbidden() {
        when(repository.findConversation(10L, 20L)).thenReturn(conversation(99L, null));
        assertThatThrownBy(() -> service.renameConversation(10L, 20L, 30L, "title"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("access denied");
    }

    private AgentConversation conversation(Long ownerId, Instant deletedAt) {
        AgentConversation value = new AgentConversation();
        value.setConversationId(20L);
        value.setProjectId(10L);
        value.setUserId(ownerId);
        value.setTitle("Old title");
        value.setDeletedAt(deletedAt);
        return value;
    }
}
