package com.penmate.backend.application.storybible;

import java.util.List;

/**
 * Stable proposal emitted when a generation turn suggests Story Bible updates.
 * <p>
 * Snapshot policy: all fields are persisted into task/result snapshot JSON so approval and recovery can inspect the
 * same proposal payload without introducing alternate naming. All fields enter snapshot; there are no runtime-only
 * fields here. Canon / proposed / assumption must be expressed only via {@code canonicalStatus}.
 */
public record StoryBibleUpdateProposal(
        List<String> entryKeys,
        String canonicalStatus,
        String reasoningSummary
) {

    public StoryBibleUpdateProposal {
        entryKeys = List.copyOf(entryKeys == null ? List.of() : entryKeys);
        canonicalStatus = normalize(canonicalStatus);
        reasoningSummary = normalize(reasoningSummary);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
