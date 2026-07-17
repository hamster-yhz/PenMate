package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;

import java.util.List;

public record StoryBibleRouteRequest(
        Long projectId,
        Long sessionId,
        Long runId,
        Long chapterId,
        String userMessage,
        List<String> userMentionedEntities,
        StoryBibleRoutingMode routingMode,
        Long storyBibleRevision,
        List<CatalogEntry> epochCatalog,
        List<Long> workingSetNodeIds,
        AgentLlmExecutionConfig selectorExecutionConfig
) {
    public StoryBibleRouteRequest {
        userMessage = userMessage == null ? "" : userMessage.trim();
        userMentionedEntities = List.copyOf(userMentionedEntities == null ? List.of() : userMentionedEntities);
        epochCatalog = List.copyOf(epochCatalog == null ? List.of() : epochCatalog);
        workingSetNodeIds = List.copyOf(workingSetNodeIds == null ? List.of() : workingSetNodeIds);
        routingMode = routingMode == null ? StoryBibleRoutingMode.RETRIEVAL_THEN_LLM : routingMode;
    }

    public record CatalogEntry(
            Long nodeId,
            String semanticFamily,
            String typeCode,
            String title,
            List<String> aliases,
            String summary,
            List<CatalogRelation> keyRelations,
            String currentChapterStateSummary,
            String inclusionPolicy,
            String canonStatus
    ) {
        public CatalogEntry {
            aliases = List.copyOf(aliases == null ? List.of() : aliases);
            keyRelations = List.copyOf(keyRelations == null ? List.of() : keyRelations);
            currentChapterStateSummary = currentChapterStateSummary == null ? "" : currentChapterStateSummary;
        }

        public CatalogEntry(Long nodeId, String title, String typeCode, String summary,
                            String inclusionPolicy, String canonStatus) {
            this(nodeId, "UNKNOWN", typeCode, title, List.of(), summary, List.of(), "",
                    inclusionPolicy, canonStatus);
        }
    }

    public record CatalogRelation(String direction, String relationType, Long otherNodeId, String otherTitle) {
    }
}
