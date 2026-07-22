package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ContextEpochSnapshotCodec {
    private final JsonCodec jsonCodec;

    public ContextEpochSnapshotCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public String encode(Snapshot snapshot) {
        try {
            return jsonCodec.write(snapshot);
        } catch (RuntimeException ex) {
            throw BusinessException.of("Failed to encode Context Epoch snapshot");
        }
    }

    public Snapshot decode(String json) {
        try {
            return jsonCodec.read(json, Snapshot.class);
        } catch (RuntimeException ex) {
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
            Map<String, Object> effectiveState,
            List<Long> appliedProgressionIds,
            List<String> stateFlags
    ) {
        public CoreNode {
            appliedProgressionIds = List.copyOf(appliedProgressionIds == null ? List.of() : appliedProgressionIds);
            stateFlags = List.copyOf(stateFlags == null ? List.of() : stateFlags);
        }

    }
}
