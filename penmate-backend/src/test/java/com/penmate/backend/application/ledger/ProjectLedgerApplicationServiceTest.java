package com.penmate.backend.application.ledger;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.ledger.model.ProjectLedger;
import com.penmate.backend.domain.ledger.repository.ProjectLedgerRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectLedgerApplicationServiceTest {
    private final ProjectLedgerRepository repository = mock(ProjectLedgerRepository.class);
    private final NovelGateway novels = mock(NovelGateway.class);
    private final BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
    private final ProjectLedgerApplicationService service = new ProjectLedgerApplicationService(repository, novels, ids);

    @BeforeEach
    void ownsProject() {
        NovelProject project = new NovelProject();
        project.setProjectId(10L);
        project.setOwnerUserId(20L);
        when(novels.findProjectById(10L)).thenReturn(project);
        when(novels.lockProject(10L)).thenReturn(project);
    }

    @Test
    void patches_unicode_code_point_ranges_atomically() {
        ProjectLedger current = ledger("A😀BC", 3L);
        ProjectLedger updated = ledger("A星BC", 4L);
        when(repository.find(10L, 30L)).thenReturn(current, updated);
        when(repository.update(10L, 30L, 3L, "Plan", "A星BC")).thenReturn(1);

        ProjectLedger result = service.update(10L, 30L, 3L, null, 1, 2, "星", 20L);

        assertThat(result.getContent()).isEqualTo("A星BC");
        verify(repository).update(10L, 30L, 3L, "Plan", "A星BC");
    }

    @Test
    void reads_no_more_than_twenty_thousand_unicode_characters() {
        when(repository.find(10L, 30L)).thenReturn(ledger("A😀BC", 3L));

        var result = service.read(10L, 30L, 1, 2, 20L);

        assertThat(result.content()).isEqualTo("😀B");
        assertThat(result.end()).isEqualTo(3);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void refuses_to_create_the_hundred_and_first_ledger() {
        when(repository.countByProject(10L)).thenReturn(100);

        assertThatThrownBy(() -> service.create(10L, "Plan", "", 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at most 100");
    }

    @Test
    void refuses_a_stale_revision_without_writing() {
        when(repository.find(10L, 30L)).thenReturn(ledger("content", 4L));

        assertThatThrownBy(() -> service.update(10L, 30L, 3L, "Other", null, null, null, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("changed after it was read");
    }

    @Test
    void rejects_an_agent_update_without_a_lease_token_before_writing() {
        assertThatThrownBy(() -> service.updateByAgent(
                10L, 30L, 3L, "Plan", null, null, null, " ", 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("leaseToken is required");
    }

    private ProjectLedger ledger(String content, Long revision) {
        ProjectLedger ledger = new ProjectLedger();
        ledger.setLedgerId(30L);
        ledger.setProjectId(10L);
        ledger.setTitle("Plan");
        ledger.setContent(content);
        ledger.setContentRevision(revision);
        return ledger;
    }
}
