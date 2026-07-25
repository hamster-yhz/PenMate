package com.penmate.backend.interfaces.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.novel.model.ChapterAiUndoOperation;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class DomainBusinessIdSerializationConfigTest {

    private static final long LARGE_ID = 9_223_372_036_854_775_000L;

    @Test
    void serializes_business_ids_as_strings_without_changing_numeric_counters() throws Exception {
        ObjectMapper mapper = mapper();

        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(LARGE_ID);
        chapter.setProjectId(LARGE_ID - 1);
        chapter.setVolumeId(LARGE_ID - 2);
        chapter.setLeaseOwnerId(LARGE_ID - 3);
        chapter.setLeaseToken("must-not-leak");
        chapter.setContentRevision(17L);

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(chapter));

        assertThat(json.path("chapterId").asText()).isEqualTo(String.valueOf(LARGE_ID));
        assertThat(json.path("projectId").isTextual()).isTrue();
        assertThat(json.path("volumeId").isTextual()).isTrue();
        assertThat(json.path("leaseOwnerId").isTextual()).isTrue();
        assertThat(json.path("contentRevision").isIntegralNumber()).isTrue();
        assertThat(json.has("leaseToken")).isFalse();
    }

    @Test
    void applies_string_id_contract_to_every_legacy_domain_response_type() throws Exception {
        ObjectMapper mapper = mapper();
        NovelProject project = new NovelProject();
        project.setProjectId(LARGE_ID);
        project.setOwnerUserId(LARGE_ID);
        NovelVolume volume = new NovelVolume();
        volume.setVolumeId(LARGE_ID);
        volume.setProjectId(LARGE_ID);
        ChapterAiUndoOperation undo = new ChapterAiUndoOperation();
        undo.setOperationId(LARGE_ID);
        undo.setProjectId(LARGE_ID);
        undo.setChapterId(LARGE_ID);
        undo.setRunId(LARGE_ID);
        PluginProjectInstall install = new PluginProjectInstall();
        install.setPluginInstallId(LARGE_ID);
        install.setProjectId(LARGE_ID);
        install.setPluginId(LARGE_ID);
        install.setInstalledBy(LARGE_ID);
        OpsAsyncJob job = new OpsAsyncJob();
        job.setJobId(LARGE_ID);
        job.setOwnerUserId(LARGE_ID);
        job.setProjectId(LARGE_ID);
        OpsMigrationTask migration = new OpsMigrationTask();
        migration.setMigrationId(LARGE_ID);

        assertStringIds(mapper, project, "projectId", "ownerUserId");
        assertStringIds(mapper, volume, "volumeId", "projectId");
        assertStringIds(mapper, undo, "operationId", "projectId", "chapterId", "runId");
        assertStringIds(mapper, install, "pluginInstallId", "projectId", "pluginId", "installedBy");
        assertStringIds(mapper, job, "jobId", "ownerUserId", "projectId");
        assertStringIds(mapper, migration, "migrationId");
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new DomainBusinessIdSerializationConfig().domainBusinessIdMixins().customize(builder);
        return builder.build();
    }

    private void assertStringIds(ObjectMapper mapper, Object value, String... fields) throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(value));
        for (String field : fields) {
            assertThat(json.path(field).isTextual()).as(field).isTrue();
            assertThat(json.path(field).asText()).as(field).isEqualTo(String.valueOf(LARGE_ID));
        }
    }
}
