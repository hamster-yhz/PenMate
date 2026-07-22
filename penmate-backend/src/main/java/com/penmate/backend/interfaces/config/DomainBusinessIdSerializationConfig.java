package com.penmate.backend.interfaces.config;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.penmate.backend.domain.novel.model.ChapterAiUndoOperation;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.model.OpsMigrationTask;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Keeps transport serialization concerns outside domain entities. */
@Configuration
public class DomainBusinessIdSerializationConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer domainBusinessIdMixins() {
        return builder -> builder
                .mixIn(NovelProject.class, NovelProjectMixin.class)
                .mixIn(NovelVolume.class, NovelVolumeMixin.class)
                .mixIn(NovelChapter.class, NovelChapterMixin.class)
                .mixIn(ChapterAiUndoOperation.class, ChapterAiUndoOperationMixin.class)
                .mixIn(PluginProjectInstall.class, PluginProjectInstallMixin.class)
                .mixIn(OpsAsyncJob.class, OpsAsyncJobMixin.class)
                .mixIn(OpsMigrationTask.class, OpsMigrationTaskMixin.class);
    }

    private abstract static class NovelProjectMixin {
        @StringId abstract Long getProjectId();
        @StringId abstract Long getOwnerUserId();
    }

    private abstract static class NovelVolumeMixin {
        @StringId abstract Long getVolumeId();
        @StringId abstract Long getProjectId();
    }

    private abstract static class NovelChapterMixin {
        @StringId abstract Long getChapterId();
        @StringId abstract Long getProjectId();
        @StringId abstract Long getVolumeId();
        @StringId abstract Long getLeaseOwnerId();
    }

    private abstract static class ChapterAiUndoOperationMixin {
        @StringId abstract Long getOperationId();
        @StringId abstract Long getProjectId();
        @StringId abstract Long getChapterId();
        @StringId abstract Long getRunId();
    }

    private abstract static class PluginProjectInstallMixin {
        @StringId abstract Long getPluginInstallId();
        @StringId abstract Long getProjectId();
        @StringId abstract Long getPluginId();
        @StringId abstract Long getInstalledBy();
    }

    private abstract static class OpsAsyncJobMixin {
        @StringId abstract Long getJobId();
        @StringId abstract Long getOwnerUserId();
        @StringId abstract Long getProjectId();
    }

    private abstract static class OpsMigrationTaskMixin {
        @StringId abstract Long getMigrationId();
    }

    @JacksonAnnotationsInside
    @Target({METHOD, FIELD})
    @Retention(RUNTIME)
    @JsonSerialize(using = ToStringSerializer.class)
    private @interface StringId {
    }
}
