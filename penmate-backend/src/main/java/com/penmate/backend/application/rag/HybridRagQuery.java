package com.penmate.backend.application.rag;

import java.util.List;

/**
 * Hybrid RAG query for agent context retrieval.
 * <p>
 * Story Bible is long-term canon knowledge, while RAG is only the retrieval mechanism.
 * Retrieved references must still be normalized by Context Builder before entering prompt assembly.
 */
public record HybridRagQuery(
        Long projectId,
        Long sessionId,
        Long runId,
        Long chapterId,
        Integer storyBibleVersion,
        List<String> activatedSkills,
        List<String> intentTags,
        List<String> userMentionedEntities,
        Integer topK,
        String queryText,
        RagSearchScope searchScope
) {

    public HybridRagQuery {
        activatedSkills = List.copyOf(activatedSkills == null ? List.of() : activatedSkills);
        intentTags = List.copyOf(intentTags == null ? List.of() : intentTags);
        userMentionedEntities = List.copyOf(userMentionedEntities == null ? List.of() : userMentionedEntities);
        topK = topK == null || topK <= 0 ? 3 : topK;
        queryText = queryText == null ? "" : queryText.trim();
        searchScope = searchScope == null ? RagSearchScope.AGENT_CONTEXT : searchScope;
    }
}
