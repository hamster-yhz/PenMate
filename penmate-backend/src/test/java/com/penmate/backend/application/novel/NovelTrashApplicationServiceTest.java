package com.penmate.backend.application.novel;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelTrashApplicationServiceTest {

    @Mock
    private NovelGateway novelGateway;

    @Mock
    private ObjectStorageService objectStorage;

    @InjectMocks
    private NovelTrashApplicationService service;

    @Test
    void lists_only_the_authenticated_owners_deleted_projects() {
        NovelProject project = deletedProject();
        when(novelGateway.findDeletedProjectsByOwner(1001L)).thenReturn(List.of(project));

        assertThat(service.listDeletedProjects(1001L)).containsExactly(project);
        verify(novelGateway).findDeletedProjectsByOwner(1001L);
    }

    @Test
    void restores_a_deleted_project_owned_by_the_actor() {
        NovelProject deleted = deletedProject();
        NovelProject restored = deletedProject();
        restored.setDeletedAt(null);
        when(novelGateway.findDeletedProjectByIdAndOwner(2001L, 1001L)).thenReturn(deleted);
        when(novelGateway.restoreProject(2001L, 1001L)).thenReturn(1);
        when(novelGateway.findProjectById(2001L)).thenReturn(restored);

        assertThat(service.restoreProject(2001L, 1001L)).isSameAs(restored);
    }

    @Test
    void rejects_permanent_delete_when_the_title_confirmation_does_not_match() {
        when(novelGateway.lockDeletedProject(2001L, 1001L, null)).thenReturn(deletedProject());

        assertThatThrownBy(() -> service.permanentlyDeleteProject(2001L, 1001L, "wrong title"))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Project title confirmation does not match");

        verify(novelGateway, never()).purgeDeletedProject(2001L, 1001L, null);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void removes_objects_before_purging_all_project_records() {
        when(novelGateway.lockDeletedProject(2001L, 1001L, null)).thenReturn(deletedProject());
        when(novelGateway.findProjectObjectKeys(2001L)).thenReturn(List.of("covers/original.webp", "rag/source.txt"));
        when(novelGateway.purgeDeletedProject(2001L, 1001L, null)).thenReturn(1);

        service.permanentlyDeleteProject(2001L, 1001L, "长夜行");

        verify(objectStorage).delete("covers/original.webp");
        verify(objectStorage).delete("rag/source.txt");
        verify(novelGateway).purgeDeletedProject(2001L, 1001L, null);
    }

    private NovelProject deletedProject() {
        NovelProject project = new NovelProject();
        project.setProjectId(2001L);
        project.setOwnerUserId(1001L);
        project.setTitle("长夜行");
        return project;
    }
}
