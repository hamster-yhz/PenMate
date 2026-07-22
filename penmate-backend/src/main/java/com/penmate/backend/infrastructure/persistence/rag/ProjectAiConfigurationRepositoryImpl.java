package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;
import com.penmate.backend.domain.rag.repository.ProjectAiConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProjectAiConfigurationRepositoryImpl implements ProjectAiConfigurationRepository {
    private final ProjectAiConfigurationMapper mapper;

    @Override public ProjectAiConfiguration findByProjectId(Long projectId) { return mapper.findByProjectId(projectId); }
    @Override public ProjectAiConfiguration findByProjectIdForUpdate(Long projectId) { return mapper.findByProjectIdForUpdate(projectId); }
    @Override public int insert(ProjectAiConfiguration configuration) { return mapper.insert(configuration); }
    @Override public int update(ProjectAiConfiguration configuration) { return mapper.update(configuration); }
}
