package com.penmate.backend.application.agent.context;

import java.util.List;

/**
 * Stable context builder output shared by workflow, snapshot persistence and recovery.
 * <p>
 * Snapshot policy: all fields are persisted. {@code styleSnapshot} is a snapshot field, while the values inside
 * {@code sources}, {@code missingContextFlags}, {@code conflicts} and {@code storyBibleEntries}
 * document how the snapshot was assembled.
 */
public record ContextPackage(
        List<String> sources,
        List<String> missingContextFlags,
        List<String> conflicts,
        List<String> storyBibleEntries,
        List<String> coreStoryBibleEntries,
        List<String> workingSetEntries,
        List<String> selectedStoryBibleEntries,
        String styleSnapshot,
        String chapterScope,
        String authorProfileSnapshot
) {

    public ContextPackage {
        sources = List.copyOf(sources == null ? List.of() : sources);
        missingContextFlags = List.copyOf(missingContextFlags == null ? List.of() : missingContextFlags);
        conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
        storyBibleEntries = List.copyOf(storyBibleEntries == null ? List.of() : storyBibleEntries);
        coreStoryBibleEntries = List.copyOf(coreStoryBibleEntries == null ? List.of() : coreStoryBibleEntries);
        workingSetEntries = List.copyOf(workingSetEntries == null ? List.of() : workingSetEntries);
        selectedStoryBibleEntries = List.copyOf(selectedStoryBibleEntries == null ? List.of() : selectedStoryBibleEntries);
        styleSnapshot = normalize(styleSnapshot);
        chapterScope = normalize(chapterScope);
        authorProfileSnapshot = normalize(authorProfileSnapshot);
    }

    public ContextPackage(List<String> sources, List<String> missingContextFlags, List<String> conflicts,
                          List<String> storyBibleEntries, List<String> coreStoryBibleEntries,
                          List<String> workingSetEntries, List<String> selectedStoryBibleEntries,
                          String styleSnapshot, String chapterScope) {
        this(sources, missingContextFlags, conflicts, storyBibleEntries, coreStoryBibleEntries,
                workingSetEntries, selectedStoryBibleEntries, styleSnapshot, chapterScope, "");
    }

    public ContextPackage(List<String> sources, List<String> missingContextFlags, List<String> conflicts,
                          List<String> storyBibleEntries, String styleSnapshot, String chapterScope) {
        this(sources, missingContextFlags, conflicts, storyBibleEntries, List.of(), List.of(),
                storyBibleEntries, styleSnapshot, chapterScope, "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
