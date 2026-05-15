package com.penmate.backend.application.storybible;

import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StoryBibleVersionSelector {

    public List<StoryBibleEntry> selectForChapter(List<StoryBibleEntry> entries, Long chapterId) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<String, StoryBibleEntry> selected = new LinkedHashMap<>();
        for (StoryBibleEntry entry : entries) {
            if (!isCanon(entry) || !isActiveAtChapter(entry, chapterId)) {
                continue;
            }
            String key = normalizeKey(entry);
            StoryBibleEntry existing = selected.get(key);
            if (existing == null || compareVersion(entry, existing) > 0) {
                selected.put(key, entry);
            }
        }
        List<StoryBibleEntry> result = new ArrayList<>(selected.values());
        result.sort(Comparator.comparing(StoryBibleEntry::getEntryKey, Comparator.nullsLast(String::compareTo)));
        return List.copyOf(result);
    }

    private boolean isActiveAtChapter(StoryBibleEntry entry, Long chapterId) {
        if (entry == null || chapterId == null) {
            return entry != null;
        }
        Long validFrom = entry.getValidFromChapterId();
        Long validTo = entry.getValidToChapterId();
        boolean started = validFrom == null || validFrom <= chapterId;
        boolean notExpired = validTo == null || validTo >= chapterId;
        return started && notExpired;
    }

    private int compareVersion(StoryBibleEntry left, StoryBibleEntry right) {
        int versionCompare = Integer.compare(nullSafeVersion(left), nullSafeVersion(right));
        if (versionCompare != 0) {
            return versionCompare;
        }
        return Long.compare(nullSafeLong(left.getValidFromChapterId()), nullSafeLong(right.getValidFromChapterId()));
    }

    private int nullSafeVersion(StoryBibleEntry entry) {
        return entry.getVersionNo() == null ? 0 : entry.getVersionNo();
    }

    private long nullSafeLong(Long value) {
        return value == null ? Long.MIN_VALUE : value;
    }

    private boolean isCanon(StoryBibleEntry entry) {
        return entry != null && "CANON".equalsIgnoreCase(entry.getCanonicalStatus());
    }

    private String normalizeKey(StoryBibleEntry entry) {
        return entry.getEntryKey() == null ? "" : entry.getEntryKey().trim();
    }
}
