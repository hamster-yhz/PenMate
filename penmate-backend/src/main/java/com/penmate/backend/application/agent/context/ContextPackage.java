package com.penmate.backend.application.agent.context;

import java.util.List;

/**
 * Stable context builder output shared by workflow, snapshot persistence and recovery.
 * <p>
 * Snapshot policy: all fields are persisted. {@code styleSnapshot} is a snapshot field, while the values inside
 * {@code sources}, {@code missingContextFlags}, {@code conflicts}, {@code storyBibleEntries} and {@code ragRefs}
 * document how the snapshot was assembled.
 */
public record ContextPackage(
        List<String> sources,
        List<String> missingContextFlags,
        List<String> conflicts,
        List<String> storyBibleEntries,
        List<String> ragRefs,
        String styleSnapshot,
        String chapterScope
) {

    public ContextPackage {
        sources = List.copyOf(sources == null ? List.of() : sources);
        missingContextFlags = List.copyOf(missingContextFlags == null ? List.of() : missingContextFlags);
        conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
        storyBibleEntries = List.copyOf(storyBibleEntries == null ? List.of() : storyBibleEntries);
        ragRefs = List.copyOf(ragRefs == null ? List.of() : ragRefs);
        styleSnapshot = normalize(styleSnapshot);
        chapterScope = normalize(chapterScope);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
