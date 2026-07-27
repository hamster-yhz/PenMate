package com.penmate.backend.application.agent.tool.runtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class StoryBibleV2ApprovalPreviewConfiguration {

    @Bean
    public ToolApprovalPreviewProvider storyBibleNodeWriteApprovalPreview() {
        return batchProvider("story_bible_node_write", "nodeId", "title");
    }

    @Bean
    public ToolApprovalPreviewProvider storyBibleRelationWriteApprovalPreview() {
        return batchProvider("story_bible_relation_write", "relationId", "relationType");
    }

    @Bean
    public ToolApprovalPreviewProvider storyBibleProgressionWriteApprovalPreview() {
        return batchProvider("story_bible_progression_write", "progressionId", "summary");
    }

    @Bean
    public ToolApprovalPreviewProvider storyBibleStructureWriteApprovalPreview() {
        return batchProvider("story_bible_structure_write", "typeId", "displayName");
    }

    private ToolApprovalPreviewProvider batchProvider(String toolCode, String idField, String labelField) {
        return new ToolApprovalPreviewProvider() {
            @Override public String toolCode() { return toolCode; }

            @Override public Map<String, String> preview(Map<String, Object> toolArguments) {
                LinkedHashMap<String, String> result = new LinkedHashMap<>();
                Object rawItems = toolArguments.get("items");
                if (!(rawItems instanceof List<?> items)) return Map.of();
                uniqueNodeId(items).ifPresent(nodeId -> result.put("nodeId", nodeId));
                result.put("执行方式", "原子批次，任一项失败则全部取消");
                result.put("变更数量", items.size() + " 项");
                int visible = Math.min(items.size(), 8);
                for (int index = 0; index < visible; index++) {
                    result.put("变更 " + (index + 1), describe(items.get(index), idField, labelField));
                }
                if (items.size() > visible) result.put("其余变更", (items.size() - visible) + " 项");
                return java.util.Collections.unmodifiableMap(result);
            }
        };
    }

    private static String describe(Object raw, String idField, String labelField) {
        if (!(raw instanceof Map<?, ?> item)) return "无效变更项";
        String operation = text(item.get("operation"), "未知操作");
        String target = text(item.get(labelField), null);
        if (target == null) target = text(item.get(idField), null);
        if (target == null && item.get("nodeId") != null) target = String.valueOf(item.get("nodeId"));
        List<String> changedFields = item.keySet().stream().map(String::valueOf)
                .filter(field -> !SetHolder.METADATA_FIELDS.contains(field))
                .sorted().toList();
        StringBuilder value = new StringBuilder(operation);
        if (target != null) value.append(" · ").append(target);
        if (!changedFields.isEmpty()) value.append(" · 字段 ").append(String.join(", ", changedFields));
        if (item.get("expectedRevision") != null) value.append(" · 基于修订 ").append(item.get("expectedRevision"));
        return value.toString();
    }

    private static String text(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return String.valueOf(value);
    }

    private static java.util.Optional<String> uniqueNodeId(List<?> items) {
        java.util.Set<String> nodeIds = items.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> firstText(item, "nodeId", "sourceNodeId", "targetNodeId"))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return nodeIds.size() == 1 ? java.util.Optional.of(nodeIds.iterator().next()) : java.util.Optional.empty();
    }

    private static String firstText(Map<?, ?> item, String... fields) {
        for (String field : fields) {
            String value = text(item.get(field), null);
            if (value != null) return value;
        }
        return null;
    }

    private static final class SetHolder {
        private static final java.util.Set<String> METADATA_FIELDS = java.util.Set.of(
                "operation", "expectedRevision", "nodeId", "relationId", "progressionId",
                "typeId", "categoryId", "tagId");
    }
}
