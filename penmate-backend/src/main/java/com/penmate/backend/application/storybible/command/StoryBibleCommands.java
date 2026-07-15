package com.penmate.backend.application.storybible.command;

import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;

import java.util.List;

public final class StoryBibleCommands {

    private StoryBibleCommands() {
    }

    public record CreateNodeType(
            String typeCode,
            StoryBibleSemanticFamily semanticFamily,
            String displayName,
            String iconCode,
            String fieldSchemaJson,
            Integer sortOrder
    ) {
    }

    public record UpdateNodeType(
            String displayName,
            String iconCode,
            String fieldSchemaJson,
            Integer sortOrder
    ) {
    }

    public record CreateNode(
            Long typeId,
            String title,
            String summary,
            String bodyMarkdown,
            String attributesJson,
            StoryBibleInclusionPolicy inclusionPolicy,
            StoryBibleCanonStatus canonStatus,
            List<String> aliases,
            List<Long> categoryIds,
            List<Long> tagIds
    ) {
    }

    public record UpdateNode(
            Long expectedRevision,
            Long typeId,
            String title,
            String summary,
            String bodyMarkdown,
            String attributesJson,
            StoryBibleInclusionPolicy inclusionPolicy,
            StoryBibleCanonStatus canonStatus,
            List<String> aliases,
            List<Long> categoryIds,
            List<Long> tagIds
    ) {
    }

    public record CreateCategory(Long parentCategoryId, String name, Integer sortOrder) {
    }

    public record UpdateCategory(Long parentCategoryId, String name, Integer sortOrder) {
    }

    public record CreateTag(String name, String color) {
    }

    public record UpdateTag(String name, String color) {
    }

    public record CreateRelation(
            Long sourceNodeId,
            String relationType,
            Long targetNodeId,
            String description,
            String attributesJson
    ) {
    }

    public record UpdateRelation(
            Long expectedRevision,
            String relationType,
            Long targetNodeId,
            String description,
            String attributesJson
    ) {
    }

    public record CreateProgression(
            Long nodeId,
            Long anchorChapterId,
            Long endChapterId,
            Long storyEventNodeId,
            String patchJson,
            String summary
    ) {
    }

    public record UpdateProgression(
            Long expectedRevision,
            Long anchorChapterId,
            Long endChapterId,
            Long storyEventNodeId,
            String patchJson,
            String summary
    ) {
    }

    public record UpdateViewPreference(
            String viewCode,
            String displayName,
            Boolean hidden,
            Integer sortOrder
    ) {
    }
}
