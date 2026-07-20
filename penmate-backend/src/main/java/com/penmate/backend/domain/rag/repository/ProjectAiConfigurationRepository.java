package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;

public interface ProjectAiConfigurationRepository {
    ProjectAiConfiguration findByProjectId(Long projectId);
    ProjectAiConfiguration findByProjectIdForUpdate(Long projectId);
    int insert(ProjectAiConfiguration configuration);
    int update(ProjectAiConfiguration configuration);
    boolean hasNonterminalRun(Long projectId);
}
