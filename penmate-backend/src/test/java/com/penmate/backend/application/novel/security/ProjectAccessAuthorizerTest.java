package com.penmate.backend.application.novel.security;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectAccessAuthorizerTest {

    @Test
    void returns_project_owned_by_actor() {
        NovelGateway novels = mock(NovelGateway.class);
        ProjectAccessAuthorizer authorizer = new ProjectAccessAuthorizer(novels);
        NovelProject project = project(101L, 201L);
        when(novels.findProjectById(101L)).thenReturn(project);

        assertThat(authorizer.requireOwnedProject(101L, 201L)).isSameAs(project);
    }

    @Test
    void hides_missing_mismatched_and_other_users_projects() {
        NovelGateway novels = mock(NovelGateway.class);
        ProjectAccessAuthorizer authorizer = new ProjectAccessAuthorizer(novels);
        when(novels.findProjectById(101L)).thenReturn(project(101L, 202L));
        when(novels.findProjectById(102L)).thenReturn(project(999L, 201L));

        assertNotFound(() -> authorizer.requireOwnedProject(null, 201L));
        assertNotFound(() -> authorizer.requireOwnedProject(100L, 201L));
        assertNotFound(() -> authorizer.requireOwnedProject(101L, 201L));
        assertNotFound(() -> authorizer.requireOwnedProject(102L, 201L));
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Novel project not found")
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    private NovelProject project(Long projectId, Long ownerUserId) {
        NovelProject project = new NovelProject();
        project.setProjectId(projectId);
        project.setOwnerUserId(ownerUserId);
        return project;
    }
}
