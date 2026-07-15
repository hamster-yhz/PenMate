package com.penmate.backend.interfaces.api.storybible.dto;

import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;

import java.util.List;

public final class StoryBibleDtos {

    private StoryBibleDtos() {
    }

    public record Bootstrap(String projectTitle) {
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

    public record UpdateNodeType(String displayName, String iconCode, String fieldSchemaJson, Integer sortOrder) {
    }

    public record CreateNode(
            String typeId,
            String title,
            String summary,
            String bodyMarkdown,
            String attributesJson,
            StoryBibleInclusionPolicy inclusionPolicy,
            StoryBibleCanonStatus canonStatus,
            List<String> aliases,
            List<String> categoryIds,
            List<String> tagIds
    ) {
    }

    public record UpdateNode(
            Long expectedRevision,
            String typeId,
            String title,
            String summary,
            String bodyMarkdown,
            String attributesJson,
            StoryBibleInclusionPolicy inclusionPolicy,
            StoryBibleCanonStatus canonStatus,
            List<String> aliases,
            List<String> categoryIds,
            List<String> tagIds
    ) {
    }

    public record CreateCategory(String parentCategoryId, String name, Integer sortOrder) {
    }

    public record UpdateCategory(String parentCategoryId, String name, Integer sortOrder) {
    }

    public record CreateTag(String name, String color) {
    }

    public record UpdateTag(String name, String color) {
    }

    public record CreateRelation(
            String sourceNodeId,
            String relationType,
            String targetNodeId,
            String description,
            String attributesJson
    ) {
    }

    public record UpdateRelation(
            Long expectedRevision,
            String relationType,
            String targetNodeId,
            String description,
            String attributesJson
    ) {
    }

    public record CreateProgression(
            String anchorChapterId,
            String endChapterId,
            String storyEventNodeId,
            String patchJson,
            String summary
    ) {
    }

    public record UpdateProgression(
            Long expectedRevision,
            String anchorChapterId,
            String endChapterId,
            String storyEventNodeId,
            String patchJson,
            String summary
    ) {
    }

    public record UpdateView(String displayName, Boolean hidden, Integer sortOrder) {
    }
}
