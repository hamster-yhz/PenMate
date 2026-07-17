package com.penmate.backend.application.storybible;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StoryBibleConflictDetector {

    public List<PatchConflict> detect(List<ProgressionPatch> progressions) {
        Map<PositionPath, Set<Long>> mutations = new HashMap<>();
        for (ProgressionPatch progression : progressions) {
            for (String path : progression.paths()) {
                mutations.computeIfAbsent(new PositionPath(progression.anchorOrdinal(), path), ignored -> new LinkedHashSet<>())
                        .add(progression.progressionId());
            }
        }
        List<PatchConflict> conflicts = new ArrayList<>();
        mutations.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> conflicts.add(new PatchConflict(
                        "SAME_POSITION_PATH_COLLISION",
                        entry.getKey().anchorOrdinal(),
                        entry.getKey().path(),
                        List.copyOf(entry.getValue())
                )));
        return List.copyOf(conflicts);
    }

    public record ProgressionPatch(Long progressionId, int anchorOrdinal, List<String> paths) {
    }

    public record PatchConflict(String code, Integer anchorOrdinal, String path, List<Long> progressionIds) {
    }

    private record PositionPath(int anchorOrdinal, String path) implements Comparable<PositionPath> {
        @Override
        public int compareTo(PositionPath other) {
            int order = Integer.compare(anchorOrdinal, other.anchorOrdinal);
            return order != 0 ? order : path.compareTo(other.path);
        }
    }
}
