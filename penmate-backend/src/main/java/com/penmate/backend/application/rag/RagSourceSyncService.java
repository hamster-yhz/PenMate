package com.penmate.backend.application.rag;

import com.penmate.backend.domain.rag.model.RagSourceContent;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import com.penmate.backend.domain.rag.repository.RagSourceCatalogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RagSourceSyncService {
    public static final String MANUSCRIPT = "MANUSCRIPT_CHUNK";
    public static final String STORY_BIBLE = "STORY_BIBLE_NODE";

    private final RagSourceSyncStore store;
    private final RagSourceCatalogRepository sources;
    private final RagIndexRepository indexes;
    private final RagIndexingService indexing;

    public RagSourceSyncService(RagSourceSyncStore store, RagSourceCatalogRepository sources,
                                RagIndexRepository indexes, RagIndexingService indexing) {
        this.store = store;
        this.sources = sources;
        this.indexes = indexes;
        this.indexing = indexing;
    }

    public void scheduleUserChapter(Long projectId, Long ownerUserId, Long chapterId) {
        scheduleUpsert(projectId, ownerUserId, MANUSCRIPT, chapterId, 30, 180);
    }

    public void scheduleAiChapter(Long projectId, Long ownerUserId, Long chapterId) {
        scheduleUpsert(projectId, ownerUserId, MANUSCRIPT, chapterId, 8, 30);
    }

    public void scheduleStoryBibleNode(Long projectId, Long ownerUserId, Long nodeId) {
        scheduleUpsert(projectId, ownerUserId, STORY_BIBLE, nodeId, 3, 20);
    }

    public void scheduleAllStoryBibleNodes(Long projectId, Long ownerUserId) {
        sources.listProjectSources(projectId).stream()
                .filter(source -> STORY_BIBLE.equals(source.sourceType()))
                .forEach(source -> store.scheduleUpsert(projectId, ownerUserId, source, 3, 20));
    }

    public void delete(Long projectId, Long ownerUserId, String sourceType, Long sourceId) {
        indexes.removeSource(projectId, sourceType, sourceId);
        store.recordDelete(projectId, ownerUserId, sourceType, sourceId);
    }

    private void scheduleUpsert(Long projectId, Long ownerUserId, String sourceType, Long sourceId,
                                long quietSeconds, long maxWaitSeconds) {
        RagSourceContent source = sources.findSource(projectId, sourceType, sourceId);
        if (source == null) {
            delete(projectId, ownerUserId, sourceType, sourceId);
            return;
        }
        store.scheduleUpsert(projectId, ownerUserId, source, quietSeconds, maxWaitSeconds);
    }

    public void processDueSources() {
        for (int index = 0; index < 4; index++) {
            RagSourceSyncStore.SyncClaim claim = store.claimDue();
            if (claim == null) return;
            try {
                if ("DELETE".equals(claim.operation())) {
                    indexes.removeSource(claim.projectId(), claim.sourceType(), claim.sourceId());
                    store.complete(claim, null);
                    continue;
                }
                boolean applied = indexing.indexSource(claim.projectId(), claim.ownerUserId(), claim.sourceType(),
                        claim.sourceId(), claim.sourceRevision(), null);
                if (applied) store.complete(claim, claim.sourceRevision());
                else store.requeue(claim, 3);
            } catch (RuntimeException exception) {
                log.warn("Incremental RAG source sync failed: projectId={}, sourceType={}, sourceId={}, reason={}",
                        claim.projectId(), claim.sourceType(), claim.sourceId(), exception.getMessage());
                store.fail(claim, exception);
            }
        }
    }
}
