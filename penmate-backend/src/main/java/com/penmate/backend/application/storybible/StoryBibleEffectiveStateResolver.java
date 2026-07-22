package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.novel.ManuscriptPositionResolver;
import com.penmate.backend.application.storybible.StoryBibleConflictDetector.PatchConflict;
import com.penmate.backend.application.storybible.StoryBibleConflictDetector.ProgressionPatch;
import com.penmate.backend.application.storybible.StoryBiblePatchValidator.ValidatedPatch;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class StoryBibleEffectiveStateResolver {

    private final ManuscriptPositionResolver positionResolver;
    private final StoryBiblePatchValidator patchValidator;
    private final StoryBibleConflictDetector conflictDetector;
    private final StoryBibleSchemaValidator schemaValidator;
    private final JsonCodec jsonCodec;

    public StoryBibleEffectiveStateResolver(
            ManuscriptPositionResolver positionResolver,
            StoryBiblePatchValidator patchValidator,
            StoryBibleConflictDetector conflictDetector,
            StoryBibleSchemaValidator schemaValidator,
            JsonCodec jsonCodec
    ) {
        this.positionResolver = Objects.requireNonNull(positionResolver, "positionResolver");
        this.patchValidator = Objects.requireNonNull(patchValidator, "patchValidator");
        this.conflictDetector = Objects.requireNonNull(conflictDetector, "conflictDetector");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
    }

    public EffectiveState resolve(
            Long projectId,
            Long targetChapterId,
            StoryBibleNode node,
            StoryBibleNodeType nodeType,
            List<StoryBibleProgression> progressions
    ) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(nodeType, "nodeType");
        Map<String, Object> baseState = baseState(node);
        ManuscriptPositionResolver.Resolution target = positionResolver.resolve(projectId, targetChapterId);
        if (!target.resolved()) {
            return new EffectiveState(baseState, List.of(),
                    List.of(new UnresolvedAnchor(null, targetChapterId, "TARGET", target.conflictCode())),
                    List.of(), false);
        }

        List<ResolvedProgression> applicable = new ArrayList<>();
        List<UnresolvedAnchor> unresolved = new ArrayList<>();
        List<PatchConflict> conflicts = new ArrayList<>();
        for (StoryBibleProgression progression : progressions == null ? List.<StoryBibleProgression>of() : progressions) {
            if (!Objects.equals(progression.getNodeId(), node.getNodeId())) continue;
            ManuscriptPositionResolver.Resolution anchor = positionResolver.resolve(projectId, progression.getAnchorChapterId());
            if (!anchor.resolved()) {
                unresolved.add(new UnresolvedAnchor(progression.getProgressionId(), progression.getAnchorChapterId(), "START", anchor.conflictCode()));
                continue;
            }
            ManuscriptPositionResolver.Resolution end = progression.getEndChapterId() == null
                    ? null : positionResolver.resolve(projectId, progression.getEndChapterId());
            if (end != null && !end.resolved()) {
                unresolved.add(new UnresolvedAnchor(progression.getProgressionId(), progression.getEndChapterId(), "END", end.conflictCode()));
                continue;
            }
            if (end != null && end.ordinal() < anchor.ordinal()) {
                conflicts.add(new PatchConflict("INVALID_PROGRESSION_RANGE", anchor.ordinal(), null, List.of(progression.getProgressionId())));
                continue;
            }
            if (target.ordinal() < anchor.ordinal() || end != null && target.ordinal() > end.ordinal()) continue;
            try {
                ValidatedPatch patch = patchValidator.validate(progression.getPatchJson(), nodeType.getFieldSchemaJson());
                applicable.add(new ResolvedProgression(progression, anchor.ordinal(), patch));
            } catch (BusinessException ex) {
                conflicts.add(new PatchConflict("INVALID_PROGRESSION_PATCH", anchor.ordinal(), null, List.of(progression.getProgressionId())));
            }
        }
        applicable.sort(Comparator.comparingInt(ResolvedProgression::anchorOrdinal)
                .thenComparing(item -> item.progression().getProgressionId()));

        conflicts.addAll(conflictDetector.detect(applicable.stream()
                .map(item -> new ProgressionPatch(item.progression().getProgressionId(), item.anchorOrdinal(), item.patch().paths()))
                .toList()));
        Set<Long> conflictedIds = new HashSet<>();
        for (PatchConflict conflict : conflicts) conflictedIds.addAll(conflict.progressionIds());

        Map<String, Object> effective = baseState;
        List<Long> applied = new ArrayList<>();
        for (ResolvedProgression progression : applicable) {
            Long progressionId = progression.progression().getProgressionId();
            if (conflictedIds.contains(progressionId)) continue;
            try {
                effective = patchValidator.apply(effective, progression.patch());
                schemaValidator.validateAttributes(jsonCodec.write(effective.get("attributes")),
                        nodeType.getFieldSchemaJson());
                applied.add(progressionId);
            } catch (BusinessException ex) {
                conflicts.add(new PatchConflict("PATCH_APPLICATION_FAILED", progression.anchorOrdinal(), null, List.of(progressionId)));
            }
        }
        boolean complete = unresolved.isEmpty() && conflicts.isEmpty();
        return new EffectiveState(effective, List.copyOf(applied), List.copyOf(unresolved), List.copyOf(conflicts), complete);
    }

    public EffectiveState resolveBase(StoryBibleNode node) {
        Objects.requireNonNull(node, "node");
        return new EffectiveState(baseState(node), List.of(), List.of(), List.of(), true);
    }

    private Map<String, Object> baseState(StoryBibleNode node) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("title", node.getTitle());
        state.put("summary", node.getSummary());
        state.put("bodyMarkdown", node.getBodyMarkdown());
        try {
            state.put("attributes", jsonCodec.readObject(
                    node.getAttributesJson() == null ? "{}" : node.getAttributesJson()));
            return Collections.unmodifiableMap(state);
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("Node attributes must be valid JSON");
        }
    }

    private record ResolvedProgression(StoryBibleProgression progression, int anchorOrdinal, ValidatedPatch patch) {
    }

    public record UnresolvedAnchor(Long progressionId, Long chapterId, String role, String code) {
    }

    public record EffectiveState(
            Map<String, Object> state,
            List<Long> appliedProgressionIds,
            List<UnresolvedAnchor> unresolvedAnchors,
            List<PatchConflict> conflicts,
            boolean complete
    ) {
        public EffectiveState {
            state = Collections.unmodifiableMap(new LinkedHashMap<>(state == null ? Map.of() : state));
        }
    }
}
