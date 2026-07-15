package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
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
            List<CoreNode> coreContext,
            List<StoryBibleRouteRequest.CatalogEntry> selectorCatalog
    ) {
        public Snapshot {
            coreContext = List.copyOf(coreContext == null ? List.of() : coreContext);
            selectorCatalog = List.copyOf(selectorCatalog == null ? List.of() : selectorCatalog);
        }
    }

    public record CoreNode(Long nodeId, Long typeId, String title, String summary,
                           String bodyMarkdown, String attributesJson) {
    }
}
