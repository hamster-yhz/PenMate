package com.penmate.backend.application.novel.security;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProjectAccessAuthorizer {

    private final NovelGateway novels;

    public ProjectAccessAuthorizer(NovelGateway novels) {
        this.novels = novels;
    }

    public NovelProject requireOwnedProject(Long projectId, Long actorUserId) {
        NovelProject project = projectId == null ? null : novels.findProjectById(projectId);
        if (project == null
                || !Objects.equals(project.getProjectId(), projectId)
                || !Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Novel project not found");
        }
        return project;
    }
}
