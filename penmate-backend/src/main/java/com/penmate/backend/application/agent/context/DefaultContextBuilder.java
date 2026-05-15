package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.rag.HybridRagResultView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds stable {@link ContextPackage} from routing request and structured Story Bible entries.
 */
@Component
public class DefaultContextBuilder {

    private final ContextBudgetPolicy budgetPolicy;

    public DefaultContextBuilder() {
        this(ContextBudgetPolicy.defaultPolicy());
    }

    public DefaultContextBuilder(ContextBudgetPolicy budgetPolicy) {
        this.budgetPolicy = Objects.requireNonNull(budgetPolicy, "budgetPolicy");
    }

    public ContextPackage build(AgentContextRoutingRequest request,
                                List<StoryBibleContextEntryView> storyBibleEntries) {
        return build(request, storyBibleEntries, List.of());
    }

    public ContextPackage build(AgentContextRoutingRequest request,
                                List<StoryBibleContextEntryView> storyBibleEntries,
                                List<HybridRagResultView> ragResults) {
        Objects.requireNonNull(request, "request");
        List<StoryBibleContextEntryView> requestedEntries = storyBibleEntries == null ? List.of() : List.copyOf(storyBibleEntries);
        List<HybridRagResultView> requestedRagResults = ragResults == null ? List.of() : List.copyOf(ragResults);

        List<String> missingContextFlags = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        Set<String> sources = new LinkedHashSet<>();
        List<String> formattedEntries = new ArrayList<>();
        List<String> ragRefs = new ArrayList<>();

        if (request.decision().includeStoryBibleContext()) {
            if (requestedEntries.isEmpty()) {
                missingContextFlags.add("story_bible_missing");
            } else {
                List<StoryBibleContextEntryView> activeEntries = requestedEntries.stream()
                        .filter(entry -> isActiveAtChapter(entry, request.chapterId()))
                        .toList();
                if (activeEntries.isEmpty()) {
                    missingContextFlags.add("story_bible_missing");
                } else {
                    Map<String, List<StoryBibleContextEntryView>> grouped = new LinkedHashMap<>();
                    for (StoryBibleContextEntryView entry : activeEntries) {
                        grouped.computeIfAbsent(normalize(entry.entryKey()), key -> new ArrayList<>()).add(entry);
                    }

                    List<StoryBibleContextEntryView> deduplicatedEntries = new ArrayList<>();
                    for (Map.Entry<String, List<StoryBibleContextEntryView>> groupedEntry : grouped.entrySet()) {
                        List<StoryBibleContextEntryView> candidates = groupedEntry.getValue();
                        candidates.sort(Comparator
                                .comparingInt(this::versionNo).reversed()
                                .thenComparing(entry -> normalize(entry.content())));
                        StoryBibleContextEntryView selected = candidates.get(0);
                        deduplicatedEntries.add(selected);
                        if (hasConflict(candidates)) {
                            conflicts.add("story_bible_conflict:" + groupedEntry.getKey());
                        }
                    }

                    deduplicatedEntries.sort(comparatorFor(request.taskProfile()));
                    int limit = Math.min(budgetPolicy.maxStoryBibleEntries(), deduplicatedEntries.size());
                    List<StoryBibleContextEntryView> limitedEntries = deduplicatedEntries.subList(0, limit);
                    for (StoryBibleContextEntryView entry : limitedEntries) {
                        if (!normalize(entry.source()).isEmpty()) {
                            sources.add(normalize(entry.source()));
                        }
                        formattedEntries.add(formatEntry(entry));
                    }
                }
            }
        }

        if (request.decision().includeRagContext()) {
            Map<String, HybridRagResultView> bestRagResults = new LinkedHashMap<>();
            for (HybridRagResultView result : requestedRagResults) {
                if (result == null || result.staleFlag()) {
                    continue;
                }
                String dedupeKey = normalize(result.sourceType()) + "::" + normalize(result.sourceId());
                HybridRagResultView existing = bestRagResults.get(dedupeKey);
                if (existing == null || result.relevanceScore() > existing.relevanceScore()) {
                    bestRagResults.put(dedupeKey, result);
                }
            }
            bestRagResults.values().stream()
                    .sorted(Comparator.comparingDouble(HybridRagResultView::relevanceScore).reversed()
                            .thenComparing(HybridRagResultView::sourceId))
                    .limit(Math.max(1, budgetPolicy.maxStoryBibleEntries()))
                    .forEach(result -> {
                        if (!normalize(result.sourceType()).isEmpty()) {
                            sources.add(normalize(result.sourceType()));
                        }
                        ragRefs.add(formatRagRef(result));
                    });
        }

        if (sources.isEmpty()) {
            sources.add("noop");
        }

        return new ContextPackage(
                List.copyOf(sources),
                List.copyOf(missingContextFlags),
                List.copyOf(conflicts),
                List.copyOf(formattedEntries),
                List.copyOf(ragRefs),
                normalizedStyle(request),
                chapterScope(request.chapterId())
        );
    }

    private Comparator<StoryBibleContextEntryView> comparatorFor(TaskProfile taskProfile) {
        boolean storyBibleQuery = taskProfile != null
                && taskProfile.skills() != null
                && taskProfile.skills().stream().map(this::normalize).anyMatch("story_bible_query"::equals);
        Comparator<StoryBibleContextEntryView> comparator = Comparator
                .comparingInt((StoryBibleContextEntryView entry) -> storyBibleQuery && isCharacterEntry(entry) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(this::riskLevel).reversed())
                .thenComparing(Comparator.comparingInt(this::versionNo).reversed())
                .thenComparing(entry -> normalize(entry.entryKey()));
        return comparator;
    }

    private boolean hasConflict(Collection<StoryBibleContextEntryView> entries) {
        Set<String> contents = new LinkedHashSet<>();
        for (StoryBibleContextEntryView entry : entries) {
            String content = normalize(entry.content());
            if (!content.isEmpty()) {
                contents.add(content);
            }
        }
        return contents.size() > 1;
    }

    private boolean isActiveAtChapter(StoryBibleContextEntryView entry, Long chapterId) {
        if (entry == null) {
            return false;
        }
        if (chapterId == null) {
            return true;
        }
        if (entry.validFromChapterId() != null && chapterId < entry.validFromChapterId()) {
            return false;
        }
        return entry.validToChapterId() == null || chapterId <= entry.validToChapterId();
    }

    private boolean isCharacterEntry(StoryBibleContextEntryView entry) {
        return "character".equalsIgnoreCase(normalize(entry.entryType()));
    }

    private int riskLevel(StoryBibleContextEntryView entry) {
        return entry == null || entry.riskLevel() == null ? 0 : entry.riskLevel();
    }

    private int versionNo(StoryBibleContextEntryView entry) {
        return entry == null || entry.versionNo() == null ? 0 : entry.versionNo();
    }

    private String formatEntry(StoryBibleContextEntryView entry) {
        String canonical = normalize(entry.canonicalStatus()).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(normalize(entry.entryKey())).append("] ");
        if (!normalize(entry.title()).isEmpty()) {
            builder.append(entry.title()).append('\n');
        }
        builder.append(normalize(entry.content()));
        if (!canonical.isEmpty()) {
            builder.append("\n(status=").append(canonical).append(')');
        }
        return builder.toString().trim();
    }

    private String formatRagRef(HybridRagResultView result) {
        return "["
                + normalize(result.sourceType())
                + ":"
                + normalize(result.sourceId())
                + "] "
                + normalize(result.content())
                + "\n(reason="
                + normalize(result.reason())
                + ", version="
                + (result.matchedVersion() == null ? "" : result.matchedVersion())
                + ", score="
                + result.relevanceScore()
                + ")";
    }

    private String normalizedStyle(AgentContextRoutingRequest request) {
        String styleSnapshot = request.decision().includeStyleContext() ? request.styleSnapshot() : null;
        return styleSnapshot == null ? "" : styleSnapshot.trim();
    }

    private String chapterScope(Long chapterId) {
        return chapterId == null ? "" : "chapter:" + chapterId;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
