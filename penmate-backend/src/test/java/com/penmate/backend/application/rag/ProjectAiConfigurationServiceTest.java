package com.penmate.backend.application.rag;

import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;
import com.penmate.backend.domain.rag.repository.ProjectAiConfigurationRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAiConfigurationServiceTest {
    @Mock ProjectAiConfigurationRepository repository;
    @Mock ModelRepository models;
    @Mock NovelGateway novels;
    @Mock BusinessIdGenerator ids;
    @Mock AsyncJobQueueService jobs;
    @InjectMocks ProjectAiConfigurationService service;

    @Test
    void initializesProjectFromUserDefaults() {
        ModelUserPreferences defaults = new ModelUserPreferences();
        defaults.setDefaultEmbeddingModelConfigId(81L);
        defaults.setDefaultContextSelectorModelConfigId(82L);
        defaults.setDefaultStoryBibleRoutingMode("RETRIEVAL_THEN_LLM");
        defaults.setDefaultChunkTargetCharacters(900);
        defaults.setDefaultChunkOverlapCharacters(150);
        defaults.setDefaultChunkMaxCharacters(1300);
        when(ids.nextId()).thenReturn(91L);
        when(models.findUserPreferences(7L)).thenReturn(defaults);
        when(models.existsAccessibleActiveConfiguration(7L, 81L, "EMBEDDING")).thenReturn(true);
        when(repository.insert(any())).thenReturn(1);

        service.initializeProject(11L, 7L);

        ArgumentCaptor<ProjectAiConfiguration> captor = ArgumentCaptor.forClass(ProjectAiConfiguration.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue()).satisfies(value -> {
            assertThat(value.getProjectAiConfigId()).isEqualTo(91L);
            assertThat(value.getCreativeModelConfigId()).isNull();
            assertThat(value.getEmbeddingModelConfigId()).isNull();
            assertThat(value.getRouterModelConfigId()).isNull();
            assertThat(value.getStoryBibleRoutingMode()).isEqualTo("LLM_SELECTOR");
            assertThat(value.getIndexStatus()).isEqualTo("REINDEX_REQUIRED");
            assertThat(value.getChunkTargetCharacters()).isEqualTo(900);
        });
    }

    @Test
    void initializesWithoutEmbeddingAsLlmSelector() {
        when(ids.nextId()).thenReturn(91L);
        when(repository.insert(any())).thenReturn(1);

        service.initializeProject(11L, 7L);

        ArgumentCaptor<ProjectAiConfiguration> captor = ArgumentCaptor.forClass(ProjectAiConfiguration.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getStoryBibleRoutingMode()).isEqualTo("LLM_SELECTOR");
        assertThat(captor.getValue().getIndexStatus()).isEqualTo("UNBOUND");
    }

    @Test
    void embeddingChangeDisablesIndexAndPermanentlySelectsLlmRouting() {
        owner(11L, 7L);
        ProjectAiConfiguration current = current();
        current.setEmbeddingModelConfigId(81L);
        current.setStoryBibleRoutingMode("RETRIEVAL");
        current.setIndexStatus("ACTIVE");
        current.setActiveIndexBuildId(101L);
        when(repository.findByProjectIdForUpdate(11L)).thenReturn(current);
        when(models.existsAccessibleActiveConfiguration(7L, 82L, "EMBEDDING")).thenReturn(true);
        when(repository.update(any())).thenReturn(1);

        service.update(11L, 7L, new ProjectAiConfigurationService.UpdateRequest(
                82L, "RETRIEVAL", null, 800, 120, 1200, 30, 8, 3, 100, null));

        ArgumentCaptor<ProjectAiConfiguration> captor = ArgumentCaptor.forClass(ProjectAiConfiguration.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getStoryBibleRoutingMode()).isEqualTo("LLM_SELECTOR");
        assertThat(captor.getValue().getIndexStatus()).isEqualTo("REINDEX_REQUIRED");
        assertThat(captor.getValue().getActiveIndexBuildId()).isNull();
    }

    @Test
    void activeRunDoesNotBlockAtomicProjectConfigurationChange() {
        owner(11L, 7L);
        when(repository.findByProjectIdForUpdate(11L)).thenReturn(current());
        when(repository.update(any())).thenReturn(1);

        service.update(11L, 7L,
                new ProjectAiConfigurationService.UpdateRequest(null, "LLM_SELECTOR", null,
                        800, 120, 1200, 30, 8, 3, 100, BigDecimal.ZERO));

        verify(repository).update(any());
    }

    private void owner(Long projectId, Long ownerId) {
        NovelProject project = new NovelProject();
        project.setProjectId(projectId);
        project.setOwnerUserId(ownerId);
        when(novels.findProjectById(projectId)).thenReturn(project);
    }

    private ProjectAiConfiguration current() {
        ProjectAiConfiguration value = new ProjectAiConfiguration();
        value.setProjectAiConfigId(91L);
        value.setProjectId(11L);
        value.setStoryBibleRoutingMode("LLM_SELECTOR");
        value.setChunkTargetCharacters(800);
        value.setChunkOverlapCharacters(120);
        value.setChunkMaxCharacters(1200);
        value.setRetrievalCandidates(30);
        value.setRetrievalTopK(8);
        value.setRetrievalMaxPerSource(3);
        value.setHnswEfSearch(100);
        value.setIndexStatus("UNBOUND");
        return value;
    }
}
