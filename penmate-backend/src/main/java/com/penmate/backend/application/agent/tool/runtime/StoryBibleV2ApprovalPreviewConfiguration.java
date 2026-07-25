package com.penmate.backend.application.agent.tool.runtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class StoryBibleV2ApprovalPreviewConfiguration {

    @Bean
    public ToolApprovalPreviewProvider storyBibleNodeWriteApprovalPreview() {
        return provider("story_bible_node_write", "nodeId", "typeId", "title", "expectedRevision");
    }

    @Bean
    public ToolApprovalPreviewProvider storyBibleRelationWriteApprovalPreview() {
        return provider("story_bible_relation_write", "relationId", "sourceNodeId", "targetNodeId",
                "relationType", "expectedRevision");
    }

    @Bean
    public ToolApprovalPreviewProvider storyBibleProgressionWriteApprovalPreview() {
        return provider("story_bible_progression_write", "progressionId", "nodeId", "anchorChapterId",
                "endChapterId", "expectedRevision");
    }

    @Bean
    public ToolApprovalPreviewProvider storyBibleStructureWriteApprovalPreview() {
        return provider("story_bible_structure_write", "typeId", "typeCode", "categoryId", "tagId", "name");
    }

    private ToolApprovalPreviewProvider provider(String toolCode, String... fields) {
        return new ToolApprovalPreviewProvider() {
            @Override public String toolCode() { return toolCode; }

            @Override public Map<String, String> preview(Map<String, Object> toolArguments) {
                LinkedHashMap<String, String> result = new LinkedHashMap<>();
                for (String field : fields) putScalar(result, field, toolArguments.get(field));
                return Map.copyOf(result);
            }
        };
    }

    private static void putScalar(Map<String, String> target, String field, Object value) {
        if ((value instanceof String || value instanceof Number || value instanceof Boolean)
                && !String.valueOf(value).isBlank()) {
            target.put(field, String.valueOf(value));
        }
    }
}
