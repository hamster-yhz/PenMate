package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextEpochSnapshotCodec {
    private final ObjectMapper objectMapper;

    public ContextEpochSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Snapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to encode Context Epoch snapshot");
        }
    }

    public Snapshot decode(String json) {
        try {
            return objectMapper.readValue(json, Snapshot.class);
        } catch (JsonProcessingException ex) {
            throw BusinessException.conflict("Context Epoch snapshot is invalid");
        }
    }

    public record Snapshot(
            int schemaVersion,
            Long projectId,
            Long storyBibleId,
            Long storyBibleRevision,
            Long manuscriptRevision,
            Long activeChapterId,
            Long activeChapterContentRevision,
            List<CoreNode> coreContext,
            List<StoryBibleRouteRequest.CatalogEntry> selectorCatalog
    ) {
        public Snapshot(int schemaVersion, Long projectId, Long storyBibleId, Long storyBibleRevision,
                        Long manuscriptRevision, Long activeChapterId, List<CoreNode> coreContext,
                        List<StoryBibleRouteRequest.CatalogEntry> selectorCatalog) {
            this(schemaVersion, projectId, storyBibleId, storyBibleRevision, manuscriptRevision,
                    activeChapterId, 0L, coreContext, selectorCatalog);
        }
        public Snapshot {
            coreContext = List.copyOf(coreContext == null ? List.of() : coreContext);
            selectorCatalog = List.copyOf(selectorCatalog == null ? List.of() : selectorCatalog);
        }
    }

    public record CoreNode(
            Long nodeId,
            Long typeId,
            String typeCode,
            String semanticFamily,
            String title,
            JsonNode effectiveState,
            List<Long> appliedProgressionIds,
            List<String> stateFlags
    ) {
        public CoreNode {
            appliedProgressionIds = List.copyOf(appliedProgressionIds == null ? List.of() : appliedProgressionIds);
            stateFlags = List.copyOf(stateFlags == null ? List.of() : stateFlags);
        }

        public CoreNode(Long nodeId, Long typeId, String title, String summary,
                        String bodyMarkdown, String attributesJson) {
            this(nodeId, typeId, "UNKNOWN", "UNKNOWN", title, legacyState(title, summary, bodyMarkdown, attributesJson),
                    List.of(), List.of());
        }

        private static JsonNode legacyState(String title, String summary, String bodyMarkdown, String attributesJson) {
            ObjectMapper mapper = new ObjectMapper();
            var state = mapper.createObjectNode();
            state.put("title", title);
            if (summary == null) state.putNull("summary"); else state.put("summary", summary);
            if (bodyMarkdown == null) state.putNull("bodyMarkdown"); else state.put("bodyMarkdown", bodyMarkdown);
            try {
                state.set("attributes", mapper.readTree(attributesJson == null ? "{}" : attributesJson));
            } catch (JsonProcessingException ex) {
                state.set("attributes", mapper.createObjectNode());
            }
            return state;
        }
    }
}
