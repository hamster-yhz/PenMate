package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;

import java.time.Instant;

public interface ProjectAiConfigurationRepository {
    ProjectAiConfiguration findByProjectId(Long projectId);
    ProjectAiConfiguration findByProjectIdForUpdate(Long projectId);
    Instant findLastCompletedAt(Long projectId);
    int insert(ProjectAiConfiguration configuration);
    int update(ProjectAiConfiguration configuration);
}
