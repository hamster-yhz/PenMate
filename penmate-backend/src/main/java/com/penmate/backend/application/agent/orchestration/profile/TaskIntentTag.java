package com.penmate.backend.application.agent.orchestration.profile;

/**
 * Stable intent tags produced by task profiling.
 * <p>
 * Snapshot policy: enum names are persisted as part of {@link TaskProfile} snapshot JSON,
 * so the serialized names must remain stable across workflow resume/recovery.
 */
public enum TaskIntentTag {
    DRAFT_GENERATION,
    STORY_BIBLE_QUERY,
    CONTINUITY_CHECK,
    STYLE_ALIGNMENT,
    RAG_LOOKUP,
    TOOL_EXECUTION,
    CLARIFICATION
}
