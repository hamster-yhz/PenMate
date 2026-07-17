package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.penmate.backend.application.common.exception.BusinessException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class StoryBibleEffectiveStateResolver {

    private final ManuscriptPositionResolver positionResolver;
    private final StoryBiblePatchValidator patchValidator;
    private final StoryBibleConflictDetector conflictDetector;
    private final StoryBibleSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    public StoryBibleEffectiveStateResolver(
            ManuscriptPositionResolver positionResolver,
            StoryBiblePatchValidator patchValidator,
            StoryBibleConflictDetector conflictDetector,
            StoryBibleSchemaValidator schemaValidator,
            ObjectMapper objectMapper
    ) {
        this.positionResolver = Objects.requireNonNull(positionResolver, "positionResolver");
        this.patchValidator = Objects.requireNonNull(patchValidator, "patchValidator");
        this.conflictDetector = Objects.requireNonNull(conflictDetector, "conflictDetector");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
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
        ObjectNode baseState = baseState(node);
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

        JsonNode effective = baseState;
        List<Long> applied = new ArrayList<>();
        for (ResolvedProgression progression : applicable) {
            Long progressionId = progression.progression().getProgressionId();
            if (conflictedIds.contains(progressionId)) continue;
            try {
                effective = patchValidator.apply(effective, progression.patch());
                schemaValidator.validateAttributes(effective.path("attributes").toString(), nodeType.getFieldSchemaJson());
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

    private ObjectNode baseState(StoryBibleNode node) {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("title", node.getTitle());
        if (node.getSummary() == null) state.putNull("summary"); else state.put("summary", node.getSummary());
        if (node.getBodyMarkdown() == null) state.putNull("bodyMarkdown"); else state.put("bodyMarkdown", node.getBodyMarkdown());
        try {
            JsonNode attributes = objectMapper.readTree(node.getAttributesJson() == null ? "{}" : node.getAttributesJson());
            if (attributes == null || !attributes.isObject()) throw BusinessException.badRequest("Node attributes must be a JSON object");
            state.set("attributes", attributes);
            return state;
        } catch (Exception ex) {
            if (ex instanceof BusinessException businessException) throw businessException;
            throw BusinessException.badRequest("Node attributes must be valid JSON");
        }
    }

    private record ResolvedProgression(StoryBibleProgression progression, int anchorOrdinal, ValidatedPatch patch) {
    }

    public record UnresolvedAnchor(Long progressionId, Long chapterId, String role, String code) {
    }

    public record EffectiveState(
            JsonNode state,
            List<Long> appliedProgressionIds,
            List<UnresolvedAnchor> unresolvedAnchors,
            List<PatchConflict> conflicts,
            boolean complete
    ) {
    }
}
