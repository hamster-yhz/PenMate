package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.repository.OpsRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock OpsRepository ops;
    @InjectMocks ProjectAiConfigurationService service;

    @Test
    void initializesProjectFromUserDefaults() {
        ModelUserPreferences defaults = new ModelUserPreferences();
        defaults.setDefaultCreativeModelConfigId(80L);
        defaults.setDefaultEmbeddingModelConfigId(81L);
        defaults.setDefaultContextSelectorModelConfigId(82L);
        defaults.setDefaultStoryBibleRoutingMode("RETRIEVAL_THEN_LLM");
        defaults.setDefaultChunkTargetCharacters(900);
        defaults.setDefaultChunkOverlapCharacters(150);
        defaults.setDefaultChunkMaxCharacters(1300);
        when(ids.nextId()).thenReturn(91L);
        when(models.findUserPreferences(7L)).thenReturn(defaults);
        when(models.existsAccessibleActiveConfiguration(7L, 81L, "EMBEDDING")).thenReturn(true);
        when(models.existsAccessibleActiveConfiguration(7L, 80L, "CHAT")).thenReturn(true);
        when(models.existsAccessibleActiveConfiguration(7L, 82L, "CHAT")).thenReturn(true);
        when(repository.insert(any())).thenReturn(1);

        service.initializeProject(11L, 7L);

        ArgumentCaptor<ProjectAiConfiguration> captor = ArgumentCaptor.forClass(ProjectAiConfiguration.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue()).satisfies(value -> {
            assertThat(value.getProjectAiConfigId()).isEqualTo(91L);
            assertThat(value.getCreativeModelConfigId()).isEqualTo(80L);
            assertThat(value.getEmbeddingModelConfigId()).isEqualTo(81L);
            assertThat(value.getRouterModelConfigId()).isEqualTo(82L);
            assertThat(value.getStoryBibleRoutingMode()).isEqualTo("AGENT_DRIVEN");
            assertThat(value.getRagEnabled()).isFalse();
            assertThat(value.getIndexStatus()).isEqualTo("REINDEX_REQUIRED");
            assertThat(value.getChunkTargetCharacters()).isEqualTo(900);
        });
    }

    @Test
    void initializesWithoutEmbeddingAsAgentDriven() {
        when(ids.nextId()).thenReturn(91L);
        when(repository.insert(any())).thenReturn(1);

        service.initializeProject(11L, 7L);

        ArgumentCaptor<ProjectAiConfiguration> captor = ArgumentCaptor.forClass(ProjectAiConfiguration.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getStoryBibleRoutingMode()).isEqualTo("AGENT_DRIVEN");
        assertThat(captor.getValue().getIndexStatus()).isEqualTo("UNBOUND");
    }

    @Test
    void embeddingChange_marks_index_for_user_rebuild_without_changing_routing() {
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
        assertThat(captor.getValue().getStoryBibleRoutingMode()).isEqualTo("RETRIEVAL");
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

    @Test
    void queuesRebuildForTheProjectEmbeddingModel() {
        owner(11L, 7L);
        ProjectAiConfiguration current = current();
        current.setEmbeddingModelConfigId(81L);
        current.setIndexStatus("REINDEX_REQUIRED");
        when(repository.findByProjectIdForUpdate(11L)).thenReturn(current);
        when(repository.update(any())).thenReturn(1);
        when(ids.nextId()).thenReturn(501L);
        OpsAsyncJob job = new OpsAsyncJob();
        job.setJobId(601L);
        job.setStatus("QUEUED");
        when(jobs.enqueue(any(), any(), any(), any(), any())).thenReturn(job);

        OpsAsyncJob result = service.requestRebuild(11L, 7L);

        ArgumentCaptor<ProjectAiConfiguration> captor = ArgumentCaptor.forClass(ProjectAiConfiguration.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getIndexStatus()).isEqualTo("QUEUED");
        assertThat(captor.getValue().getActiveIndexBuildId()).isNull();
        assertThat(result.getJobId()).isEqualTo(601L);
    }

    @Test
    void exposesRunningRebuildProgressThroughProjectConfiguration() {
        ProjectAiConfiguration configuration = current();
        configuration.setIndexStatus("QUEUED");
        OpsAsyncJob job = new OpsAsyncJob();
        job.setJobId(601L);
        job.setStatus("RUNNING");
        job.setProgressCurrent(12L);
        job.setProgressTotal(30L);
        job.setProgressMessage("Embedding project sources");
        when(ops.findLatestProjectJob(11L, "RAG_REBUILD_PROJECT")).thenReturn(job);

        var result = service.rebuildState(configuration);

        assertThat(result.status()).isEqualTo("BUILDING");
        assertThat(result.progressCurrent()).isEqualTo(12L);
        assertThat(result.progressTotal()).isEqualTo(30L);
    }

    @Test
    void cancelsOwnedProjectRebuildAndRestoresReindexRequiredState() {
        owner(11L, 7L);
        ProjectAiConfiguration configuration = current();
        configuration.setIndexStatus("QUEUED");
        when(repository.findByProjectIdForUpdate(11L)).thenReturn(configuration);
        when(repository.update(any())).thenReturn(1);
        OpsAsyncJob job = rebuildJob(601L, 11L, 7L, "RUNNING");
        OpsAsyncJob cancelling = rebuildJob(601L, 11L, 7L, "RUNNING");
        cancelling.setCancelRequestedAt(java.time.Instant.parse("2026-07-25T06:00:00Z"));
        when(ops.findJobById(601L)).thenReturn(job, cancelling);

        OpsAsyncJob result = service.cancelRebuild(11L, 7L, 601L);

        verify(jobs).requestCancel(601L);
        verify(repository).update(configuration);
        assertThat(configuration.getIndexStatus()).isEqualTo("REINDEX_REQUIRED");
        assertThat(result.cancellationRequested()).isTrue();
    }

    @Test
    void rejectsCancellationForAnotherProjectJob() {
        owner(11L, 7L);
        when(repository.findByProjectIdForUpdate(11L)).thenReturn(current());
        when(ops.findJobById(601L)).thenReturn(rebuildJob(601L, 12L, 7L, "RUNNING"));

        assertThatThrownBy(() -> service.cancelRebuild(11L, 7L, 601L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Project index rebuild job not found");
    }

    @Test
    void exposesCancellationAsItsOwnState() {
        ProjectAiConfiguration configuration = current();
        configuration.setIndexStatus("REINDEX_REQUIRED");
        OpsAsyncJob job = rebuildJob(601L, 11L, 7L, "CANCELLED");
        when(ops.findLatestProjectJob(11L, "RAG_REBUILD_PROJECT")).thenReturn(job);

        var result = service.rebuildState(configuration);

        assertThat(result.status()).isEqualTo("CANCELLED");
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
        value.setRagEnabled(true);
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

    private OpsAsyncJob rebuildJob(Long jobId, Long projectId, Long ownerUserId, String status) {
        OpsAsyncJob job = new OpsAsyncJob();
        job.setJobId(jobId);
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerUserId);
        job.setJobType("RAG_REBUILD_PROJECT");
        job.setStatus(status);
        return job;
    }
}
