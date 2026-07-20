package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PgVectorStoryBibleSemanticRetriever implements StoryBibleSemanticRetriever {
    private final RagRetrievalService retrieval;
    private final NovelGateway novels;

    public PgVectorStoryBibleSemanticRetriever(RagRetrievalService retrieval, NovelGateway novels) {
        this.retrieval = retrieval;
        this.novels = novels;
    }

    @Override
    public SemanticResult retrieve(Long projectId, Long storyBibleId, String query, int limit) {
        if (query == null || query.isBlank()) return new SemanticResult(true, List.of());
        NovelProject project = novels.findProjectById(projectId);
        if (project == null) return new SemanticResult(false, List.of());
        try {
            List<StoryBibleCandidateRetriever.Candidate> candidates = retrieval.retrieve(
                            projectId, project.getOwnerUserId(), null, query, List.of("STORY_BIBLE_NODE"), null)
                    .chunks().stream()
                    .map(chunk -> new StoryBibleCandidateRetriever.Candidate(chunk.getSourceId(),
                            60d / (1d + Math.max(0d, chunk.getDistance() == null ? 1d : chunk.getDistance())),
                            "pgvector"))
                    .distinct().limit(limit).toList();
            return new SemanticResult(true, candidates);
        } catch (BusinessException exception) {
            if ("RAG_INDEX_UNAVAILABLE".equals(exception.getErrorCode())) return new SemanticResult(false, List.of());
            throw exception;
        }
    }
}
