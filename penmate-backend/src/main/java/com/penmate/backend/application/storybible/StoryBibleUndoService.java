package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.*;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class StoryBibleUndoService {
    private static final Duration UNDO_WINDOW = Duration.ofDays(7);

    private final StoryBibleRepository repository;
    private final StoryBibleChangesetService changesets;
    private final BusinessIdGenerator ids;
    private final JsonCodec json;

    public StoryBibleUndoService(StoryBibleRepository repository,
                                 StoryBibleChangesetService changesets,
                                 BusinessIdGenerator ids,
                                 JsonCodec json) {
        this.repository = repository;
        this.changesets = changesets;
        this.ids = ids;
        this.json = json;
    }

    @Transactional
    public StoryBibleChangeset undo(Long projectId, Long changesetId, Long actorId) {
        StoryBible root = repository.findByProjectId(projectId);
        if (root == null) throw BusinessException.notFound("Story Bible not found");
        StoryBibleChangeset target = repository.findChangeset(root.getStoryBibleId(), changesetId);
        if (target == null) throw BusinessException.notFound("Story Bible changeset not found");
        validateEligibility(target, Instant.now());
        if (!Objects.equals(root.getContentRevision(), target.getContentRevision())) {
            throw BusinessException.conflict("故事圣经已有后续变更，不能撤回当前记录");
        }
        return undoChangesets(root, List.of(target), actorId, "Undid changeset " + changesetId);
    }

    @Transactional
    public RunUndoResult undoRun(Long projectId, Long sourceRunId, Long actorId) {
        StoryBible root = repository.findByProjectId(projectId);
        if (root == null) throw BusinessException.notFound("Story Bible not found");
        List<StoryBibleChangeset> runChangesets = new ArrayList<>(
                repository.findChangesetsBySourceRun(root.getStoryBibleId(), sourceRunId));
        if (runChangesets.isEmpty()) throw BusinessException.notFound("该 AI 任务没有故事圣经变更");
        runChangesets.sort(Comparator.comparing(StoryBibleChangeset::getContentRevision)
                .thenComparing(StoryBibleChangeset::getId));

        Instant now = Instant.now();
        for (StoryBibleChangeset changeset : runChangesets) {
            if (changeset.getActorType() != StoryBibleActorType.AGENT) {
                throw BusinessException.conflict("只能按任务撤回 AI 写入的故事圣经变更");
            }
            validateEligibility(changeset, now);
        }
        StoryBibleChangeset first = runChangesets.get(0);
        StoryBibleChangeset last = runChangesets.get(runChangesets.size() - 1);
        if (!Objects.equals(root.getContentRevision(), last.getContentRevision())) {
            throw BusinessException.conflict("故事圣经已有后续变更，不能撤回该 AI 任务");
        }
        List<Long> runIds = runChangesets.stream().map(StoryBibleChangeset::getChangesetId).toList();
        List<Long> tailIds = repository.findChangesetsByRevisionRange(root.getStoryBibleId(),
                        first.getContentRevision(), last.getContentRevision()).stream()
                .map(StoryBibleChangeset::getChangesetId).toList();
        if (!runIds.equals(tailIds)) {
            throw BusinessException.conflict("该 AI 任务的变更之间夹有其他修改，不能整体撤回");
        }

        StoryBibleChangeset undo = undoChangesets(root, runChangesets, actorId,
                "Undid Story Bible changes from Agent Run " + sourceRunId);
        return new RunUndoResult(sourceRunId, runIds, undo);
    }

    private StoryBibleChangeset undoChangesets(StoryBible root, List<StoryBibleChangeset> targets,
                                                Long actorId, String summary) {
        List<Long> targetIds = targets.stream().map(StoryBibleChangeset::getChangesetId).toList();
        List<StoryBibleChangeItem> items = repository.findChangeItemsByChangesetIds(targetIds);
        if (items.isEmpty()) throw BusinessException.conflict("该变更没有可撤回内容");
        Map<Long, List<StoryBibleChangeItem>> itemsByChangeset = new HashMap<>();
        for (StoryBibleChangeItem item : items) {
            itemsByChangeset.computeIfAbsent(item.getChangesetId(), ignored -> new ArrayList<>()).add(item);
        }

        List<StoryBibleChangeset> reverseTargets = new ArrayList<>(targets);
        reverseTargets.sort(Comparator.comparing(StoryBibleChangeset::getContentRevision).reversed()
                .thenComparing(StoryBibleChangeset::getId, Comparator.reverseOrder()));
        for (StoryBibleChangeset target : reverseTargets) {
            undoChangesetState(root, actorId,
                    itemsByChangeset.getOrDefault(target.getChangesetId(), List.of()));
        }

        List<StoryBibleChangesetService.ChangeDraft> inverseDrafts = new ArrayList<>();
        for (StoryBibleChangeset target : reverseTargets) {
            for (StoryBibleChangeItem item : itemsByChangeset.getOrDefault(target.getChangesetId(), List.of())) {
                inverseDrafts.add(new StoryBibleChangesetService.ChangeDraft(
                        item.getEntityType(), item.getEntityId(), inverseOperation(item.getOperation()),
                        item.getFieldPath(), item.getAfterJson(), item.getBeforeJson()));
            }
        }
        StoryBibleChangeset undo = changesets.append(root, StoryBibleActorType.USER, actorId, null,
                summary, inverseDrafts);
        if (repository.markChangesetsUndone(root.getStoryBibleId(), targetIds, actorId,
                undo.getChangesetId()) != targetIds.size()) {
            throw BusinessException.conflict("故事圣经变更已无法撤回");
        }
        return undo;
    }

    private void undoChangesetState(StoryBible root, Long actorId, List<StoryBibleChangeItem> items) {
        if (items.isEmpty()) throw BusinessException.conflict("变更记录缺少可撤回内容");
        Map<EntityKey, List<StoryBibleChangeItem>> groups = new LinkedHashMap<>();
        for (StoryBibleChangeItem item : items) {
            if ("STORY_BIBLE".equals(item.getEntityType()) || "VIEW".equals(item.getEntityType())) {
                throw BusinessException.conflict("该类型的故事圣经变更不支持撤回");
            }
            groups.computeIfAbsent(new EntityKey(item.getEntityType(), item.getEntityId()), ignored -> new ArrayList<>())
                    .add(item);
        }
        List<Map.Entry<EntityKey, List<StoryBibleChangeItem>>> ordered = new ArrayList<>(groups.entrySet());
        ordered.sort(Comparator.comparingInt(this::inversePriority));

        for (var group : ordered) validateGroup(root, group.getKey(), group.getValue());
        for (var group : ordered) applyInverse(root, actorId, group.getKey(), group.getValue());
    }

    private void validateEligibility(StoryBibleChangeset target, Instant now) {
        if (target.getArchivedAt() != null) throw BusinessException.conflict("归档变更不可撤回");
        if (target.getUndoneAt() != null) throw BusinessException.conflict("该变更已经撤回");
        if (target.getCreatedAt() == null || target.getCreatedAt().isBefore(now.minus(UNDO_WINDOW))) {
            throw BusinessException.conflict("变更已超过 7 天撤回期限");
        }
    }

    private int inversePriority(Map.Entry<EntityKey, List<StoryBibleChangeItem>> entry) {
        StoryBibleChangeOperation operation = entry.getValue().get(0).getOperation();
        String type = entry.getKey().type();
        if (operation == StoryBibleChangeOperation.DELETE || operation == StoryBibleChangeOperation.ARCHIVE) {
            if (Set.of("CATEGORY", "TAG", "NODE_TYPE", "NODE").contains(type)) return 0;
            if (Set.of("RELATION", "PROGRESSION").contains(type)) return 1;
        }
        if (operation == StoryBibleChangeOperation.CREATE) {
            if (Set.of("RELATION", "PROGRESSION").contains(type)) return 0;
            if ("NODE".equals(type)) return 1;
            return 2;
        }
        return 2;
    }

    private void validateGroup(StoryBible root, EntityKey key, List<StoryBibleChangeItem> items) {
        Object current = currentEntity(root.getStoryBibleId(), key);
        StoryBibleChangeOperation operation = items.get(0).getOperation();
        if (operation == StoryBibleChangeOperation.CREATE || operation == StoryBibleChangeOperation.UPDATE) {
            if (current == null || isDeleted(current)) throw conflict(key);
            Map<String, Object> currentMap = objectMap(current);
            for (StoryBibleChangeItem item : items) {
                if (isOrganizationPath(item.getFieldPath())) {
                    Object actual = organizationValue(root.getStoryBibleId(), key.id(), item.getFieldPath());
                    if (!same(actual, read(item.getAfterJson()))) throw conflict(key);
                } else if ("/".equals(item.getFieldPath())) {
                    if (!same(semantic(key.type(), currentMap), semantic(key.type(), object(item.getAfterJson())))) throw conflict(key);
                } else if (!same(currentMap.get(pointerField(item.getFieldPath())), read(item.getAfterJson()))) {
                    throw conflict(key);
                }
            }
            return;
        }
        if (operation == StoryBibleChangeOperation.DELETE || operation == StoryBibleChangeOperation.ARCHIVE) {
            if (current == null || !isDeleted(current)) throw conflict(key);
            return;
        }
        throw BusinessException.conflict("该变更操作不支持撤回");
    }

    private void applyInverse(StoryBible root, Long actorId, EntityKey key, List<StoryBibleChangeItem> items) {
        StoryBibleChangeOperation operation = items.get(0).getOperation();
        Object current = currentEntity(root.getStoryBibleId(), key);
        if (operation == StoryBibleChangeOperation.CREATE) {
            applyOrganization(root, key, items, true);
            deleteCreated(root, actorId, key, current);
            return;
        }
        if (operation == StoryBibleChangeOperation.DELETE || operation == StoryBibleChangeOperation.ARCHIVE) {
            StoryBibleChangeItem full = items.stream().filter(item -> "/".equals(item.getFieldPath())).findFirst()
                    .orElseThrow(() -> BusinessException.conflict("缺少可撤回快照"));
            restoreDeleted(root, actorId, key, current, full.getBeforeJson());
            applyOrganization(root, key, items, true);
            return;
        }
        applyUpdate(root, actorId, key, current, items);
        applyOrganization(root, key, items, true);
    }

    private void applyUpdate(StoryBible root, Long actorId, EntityKey key, Object current,
                             List<StoryBibleChangeItem> items) {
        Map<String, Object> target = objectMap(current);
        for (StoryBibleChangeItem item : items) {
            if (isOrganizationPath(item.getFieldPath())) continue;
            if ("/".equals(item.getFieldPath())) {
                target.putAll(object(item.getBeforeJson()));
                continue;
            }
            String field = pointerField(item.getFieldPath());
            if (item.getBeforeJson() == null) target.remove(field);
            else target.put(field, read(item.getBeforeJson()));
        }
        switch (key.type()) {
            case "NODE" -> {
                StoryBibleNode value = convert(target, StoryBibleNode.class);
                value.setRevision(((StoryBibleNode) current).getRevision());
                value.setUpdatedBy(actorId);
                require(repository.updateNode(value, ((StoryBibleNode) current).getRevision()), key);
            }
            case "RELATION" -> {
                StoryBibleRelation value = convert(target, StoryBibleRelation.class);
                value.setRevision(((StoryBibleRelation) current).getRevision());
                value.setUpdatedBy(actorId);
                require(repository.updateRelation(value, ((StoryBibleRelation) current).getRevision()), key);
            }
            case "PROGRESSION" -> {
                StoryBibleProgression value = convert(target, StoryBibleProgression.class);
                value.setRevision(((StoryBibleProgression) current).getRevision());
                value.setUpdatedBy(actorId);
                require(repository.updateProgression(value, ((StoryBibleProgression) current).getRevision()), key);
            }
            case "CATEGORY" -> require(repository.updateCategory(convert(target, StoryBibleCategory.class)), key);
            case "TAG" -> require(repository.updateTag(convert(target, StoryBibleTag.class)), key);
            case "NODE_TYPE" -> require(repository.updateNodeType(convert(target, StoryBibleNodeType.class)), key);
            default -> throw BusinessException.conflict("不支持撤回的实体类型: " + key.type());
        }
    }

    private void deleteCreated(StoryBible root, Long actorId, EntityKey key, Object current) {
        switch (key.type()) {
            case "NODE" -> require(repository.softDeleteNode(root.getStoryBibleId(), key.id(), ((StoryBibleNode) current).getRevision(), actorId), key);
            case "RELATION" -> require(repository.softDeleteRelation(root.getStoryBibleId(), key.id(), ((StoryBibleRelation) current).getRevision(), actorId), key);
            case "PROGRESSION" -> require(repository.softDeleteProgression(root.getStoryBibleId(), key.id(), ((StoryBibleProgression) current).getRevision(), actorId), key);
            case "CATEGORY" -> { repository.deleteNodeCategoriesByCategory(root.getStoryBibleId(), key.id()); require(repository.softDeleteCategory(root.getStoryBibleId(), key.id()), key); }
            case "TAG" -> { repository.deleteNodeTagsByTag(root.getStoryBibleId(), key.id()); require(repository.softDeleteTag(root.getStoryBibleId(), key.id()), key); }
            case "NODE_TYPE" -> require(repository.archiveNodeType(root.getStoryBibleId(), key.id()), key);
            default -> throw BusinessException.conflict("不支持撤回的实体类型: " + key.type());
        }
    }

    private void restoreDeleted(StoryBible root, Long actorId, EntityKey key, Object current, String snapshotJson) {
        switch (key.type()) {
            case "NODE" -> {
                StoryBibleNode value = convert(object(snapshotJson), StoryBibleNode.class);
                value.setUpdatedBy(actorId);
                require(repository.restoreNode(value, ((StoryBibleNode) current).getRevision()), key);
            }
            case "RELATION" -> {
                StoryBibleRelation value = convert(object(snapshotJson), StoryBibleRelation.class);
                value.setUpdatedBy(actorId);
                require(repository.restoreRelation(value, ((StoryBibleRelation) current).getRevision()), key);
            }
            case "PROGRESSION" -> {
                StoryBibleProgression value = convert(object(snapshotJson), StoryBibleProgression.class);
                value.setUpdatedBy(actorId);
                require(repository.restoreProgression(value, ((StoryBibleProgression) current).getRevision()), key);
            }
            case "CATEGORY" -> require(repository.restoreCategory(convert(object(snapshotJson), StoryBibleCategory.class)), key);
            case "TAG" -> require(repository.restoreTag(convert(object(snapshotJson), StoryBibleTag.class)), key);
            case "NODE_TYPE" -> require(repository.restoreNodeType(convert(object(snapshotJson), StoryBibleNodeType.class)), key);
            default -> throw BusinessException.conflict("不支持撤回的实体类型: " + key.type());
        }
    }

    private void applyOrganization(StoryBible root, EntityKey key, List<StoryBibleChangeItem> items, boolean before) {
        if (!"NODE".equals(key.type())) return;
        for (StoryBibleChangeItem item : items) {
            if (!isOrganizationPath(item.getFieldPath())) continue;
            Object value = read(before ? item.getBeforeJson() : item.getAfterJson());
            switch (item.getFieldPath()) {
                case "/aliases" -> replaceAliases(root.getStoryBibleId(), key.id(), stringList(value));
                case "/categoryIds" -> replaceCategories(root.getStoryBibleId(), key.id(), longList(value));
                case "/tagIds" -> replaceTags(root.getStoryBibleId(), key.id(), longList(value));
                default -> throw BusinessException.conflict("未知的节点组织字段");
            }
        }
    }

    private void replaceAliases(Long storyBibleId, Long nodeId, List<String> values) {
        for (StoryBibleAlias alias : repository.findAliases(storyBibleId, nodeId)) repository.softDeleteAlias(storyBibleId, alias.getAliasId());
        for (String value : values) {
            StoryBibleAlias alias = new StoryBibleAlias();
            alias.setAliasId(ids.nextId()); alias.setStoryBibleId(storyBibleId); alias.setNodeId(nodeId);
            alias.setAlias(value); alias.setNormalizedAlias(value.trim().toLowerCase(Locale.ROOT));
            require(repository.insertAlias(alias), new EntityKey("NODE", nodeId));
        }
    }

    private void replaceCategories(Long storyBibleId, Long nodeId, List<Long> values) {
        repository.deleteNodeCategories(storyBibleId, nodeId);
        for (Long value : values) {
            StoryBibleNodeCategory membership = new StoryBibleNodeCategory();
            membership.setStoryBibleId(storyBibleId); membership.setNodeId(nodeId); membership.setCategoryId(value);
            require(repository.insertNodeCategory(membership), new EntityKey("NODE", nodeId));
        }
    }

    private void replaceTags(Long storyBibleId, Long nodeId, List<Long> values) {
        repository.deleteNodeTags(storyBibleId, nodeId);
        for (Long value : values) {
            StoryBibleNodeTag membership = new StoryBibleNodeTag();
            membership.setStoryBibleId(storyBibleId); membership.setNodeId(nodeId); membership.setTagId(value);
            require(repository.insertNodeTag(membership), new EntityKey("NODE", nodeId));
        }
    }

    private Object currentEntity(Long storyBibleId, EntityKey key) {
        return switch (key.type()) {
            case "NODE" -> repository.findNodeIncludingDeleted(storyBibleId, key.id());
            case "RELATION" -> repository.findRelationIncludingDeleted(storyBibleId, key.id());
            case "PROGRESSION" -> repository.findProgressionIncludingDeleted(storyBibleId, key.id());
            case "CATEGORY" -> repository.findCategoryIncludingDeleted(storyBibleId, key.id());
            case "TAG" -> repository.findTagIncludingDeleted(storyBibleId, key.id());
            case "NODE_TYPE" -> repository.findNodeTypeIncludingArchived(storyBibleId, key.id());
            default -> null;
        };
    }

    private boolean isDeleted(Object value) {
        if (value instanceof StoryBibleNode item) return item.getDeletedAt() != null;
        if (value instanceof StoryBibleRelation item) return item.getDeletedAt() != null;
        if (value instanceof StoryBibleProgression item) return item.getDeletedAt() != null;
        if (value instanceof StoryBibleCategory item) return item.getDeletedAt() != null;
        if (value instanceof StoryBibleTag item) return item.getDeletedAt() != null;
        if (value instanceof StoryBibleNodeType item) return item.getArchivedAt() != null;
        return false;
    }

    private Object organizationValue(Long storyBibleId, Long nodeId, String path) {
        return switch (path) {
            case "/aliases" -> repository.findAliases(storyBibleId, nodeId).stream().map(StoryBibleAlias::getAlias).sorted().toList();
            case "/categoryIds" -> repository.findNodeCategories(storyBibleId, nodeId).stream().map(StoryBibleNodeCategory::getCategoryId).distinct().sorted().toList();
            case "/tagIds" -> repository.findNodeTags(storyBibleId, nodeId).stream().map(StoryBibleNodeTag::getTagId).distinct().sorted().toList();
            default -> null;
        };
    }

    private Map<String, Object> semantic(String type, Map<String, Object> value) {
        Set<String> fields = switch (type) {
            case "NODE" -> Set.of("nodeId", "storyBibleId", "typeId", "title", "summary", "bodyMarkdown", "attributesJson", "inclusionPolicy", "canonStatus", "revision");
            case "RELATION" -> Set.of("relationId", "storyBibleId", "sourceNodeId", "relationType", "targetNodeId", "description", "attributesJson", "revision");
            case "PROGRESSION" -> Set.of("progressionId", "storyBibleId", "nodeId", "anchorChapterId", "endChapterId", "storyEventNodeId", "patchJson", "summary", "revision");
            case "CATEGORY" -> Set.of("categoryId", "storyBibleId", "parentCategoryId", "name", "sortOrder");
            case "TAG" -> Set.of("tagId", "storyBibleId", "name", "normalizedName", "color");
            case "NODE_TYPE" -> Set.of("typeId", "storyBibleId", "typeCode", "semanticFamily", "displayName", "iconCode", "fieldSchemaJson", "system", "sortOrder");
            default -> Set.of();
        };
        Map<String, Object> result = new TreeMap<>();
        for (String field : fields) if (value.containsKey(field)) result.put(field, value.get(field));
        return result;
    }

    private StoryBibleChangeOperation inverseOperation(StoryBibleChangeOperation operation) {
        return switch (operation) {
            case CREATE -> StoryBibleChangeOperation.DELETE;
            case DELETE, ARCHIVE -> StoryBibleChangeOperation.RESTORE;
            case RESTORE -> StoryBibleChangeOperation.ARCHIVE;
            case UPDATE -> StoryBibleChangeOperation.UPDATE;
        };
    }

    private Map<String, Object> objectMap(Object value) { return object(json.write(value)); }
    private Map<String, Object> object(String value) { return value == null ? Map.of() : json.readObject(value); }
    private Object read(String value) { return value == null ? null : json.read(value); }
    private <T> T convert(Map<String, Object> value, Class<T> type) { return json.read(json.write(value), type); }
    private boolean same(Object left, Object right) { return Objects.equals(json.writeCanonical(left), json.writeCanonical(right)); }
    private boolean isOrganizationPath(String path) { return Set.of("/aliases", "/categoryIds", "/tagIds").contains(path); }
    private String pointerField(String path) { return path.substring(1).replace("~1", "/").replace("~0", "~"); }
    private List<String> stringList(Object value) { return value instanceof List<?> list ? list.stream().map(String::valueOf).sorted().toList() : List.of(); }
    private List<Long> longList(Object value) { return value instanceof List<?> list ? list.stream().map(item -> ((Number) item).longValue()).distinct().sorted().toList() : List.of(); }
    private void require(int affected, EntityKey key) { if (affected != 1) throw conflict(key); }
    private BusinessException conflict(EntityKey key) { return BusinessException.conflict(key.type() + " " + key.id() + " 已发生变化，撤回已取消"); }

    private record EntityKey(String type, Long id) {
    }

    public record RunUndoResult(Long sourceRunId, List<Long> changesetIds,
                                StoryBibleChangeset undoChangeset) {
        public RunUndoResult {
            changesetIds = List.copyOf(changesetIds == null ? List.of() : changesetIds);
        }
    }
}
