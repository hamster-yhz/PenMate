package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.storybible.StoryBibleChangesetService.ChangeDraft;
import com.penmate.backend.application.storybible.command.StoryBibleCommands;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeOperation;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeTag;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import com.penmate.backend.domain.storybible.model.StoryBibleTag;
import com.penmate.backend.domain.storybible.model.StoryBibleViewPreference;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class StoryBibleApplicationService {

    private static final String EMPTY_OBJECT = "{}";

    private final StoryBibleRepository repository;
    private final StoryBibleChangesetService changesetService;
    private final BusinessIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final StoryBibleSchemaValidator schemaValidator;
    private final StoryBiblePatchValidator patchValidator;
    private final StoryBibleEffectiveStateResolver effectiveStateResolver;

    public StoryBibleApplicationService(
            StoryBibleRepository repository,
            StoryBibleChangesetService changesetService,
            BusinessIdGenerator idGenerator,
            ObjectMapper objectMapper,
            StoryBibleSchemaValidator schemaValidator,
            StoryBiblePatchValidator patchValidator,
            StoryBibleEffectiveStateResolver effectiveStateResolver
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.changesetService = Objects.requireNonNull(changesetService, "changesetService");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
        this.patchValidator = Objects.requireNonNull(patchValidator, "patchValidator");
        this.effectiveStateResolver = Objects.requireNonNull(effectiveStateResolver, "effectiveStateResolver");
    }

    public StoryBible get(Long projectId) {
        StoryBible storyBible = repository.findByProjectId(projectId);
        if (storyBible == null) {
            throw BusinessException.notFound("Story Bible not found");
        }
        return storyBible;
    }

    @Transactional
    public StoryBible bootstrap(Long projectId, String projectTitle, Long actorId) {
        StoryBible existing = repository.findByProjectId(projectId);
        if (existing != null) {
            return existing;
        }
        StoryBible storyBible = new StoryBible();
        storyBible.setStoryBibleId(idGenerator.nextId());
        storyBible.setProjectId(projectId);
        storyBible.setTitle((projectTitle == null || projectTitle.isBlank() ? "Untitled" : projectTitle.trim()) + " Story Bible");
        storyBible.setDescription("");
        storyBible.setContentRevision(1L);
        requireOne(repository.insertStoryBible(storyBible), "Failed to create Story Bible");

        for (SystemNodeType seed : systemNodeTypes()) {
            StoryBibleNodeType type = new StoryBibleNodeType();
            type.setTypeId(idGenerator.nextId());
            type.setStoryBibleId(storyBible.getStoryBibleId());
            type.setTypeCode(seed.code());
            type.setSemanticFamily(seed.family());
            type.setDisplayName(seed.displayName());
            type.setIconCode(seed.iconCode());
            type.setFieldSchemaJson(EMPTY_OBJECT);
            type.setSystem(true);
            type.setSortOrder(seed.sortOrder());
            requireOne(repository.insertNodeType(type), "Failed to seed Story Bible node type");
        }
        int viewOrder = 10;
        for (StoryBibleSemanticFamily family : StoryBibleSemanticFamily.values()) {
            StoryBibleViewPreference preference = new StoryBibleViewPreference();
            preference.setStoryBibleId(storyBible.getStoryBibleId());
            preference.setViewCode(family.name());
            preference.setDisplayName(defaultFamilyName(family));
            preference.setHidden(false);
            preference.setSortOrder(viewOrder);
            preference.setUpdatedBy(actorId);
            requireOne(repository.upsertViewPreference(preference), "Failed to seed Story Bible view");
            viewOrder += 10;
        }
        changesetService.appendInitial(
                storyBible,
                StoryBibleActorType.USER,
                actorId,
                "Initial Story Bible",
                List.of(draft("STORY_BIBLE", storyBible.getStoryBibleId(), StoryBibleChangeOperation.CREATE, null, storyBible))
        );
        return storyBible;
    }

    public List<StoryBibleNodeType> listNodeTypes(Long projectId) {
        return repository.findNodeTypes(get(projectId).getStoryBibleId());
    }

    @Transactional
    public StoryBibleNodeType createNodeType(Long projectId, StoryBibleCommands.CreateNodeType command, Long actorId) {
        return createNodeType(projectId, command, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public StoryBibleNodeType createNodeType(Long projectId, StoryBibleCommands.CreateNodeType command,
                                             StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        schemaValidator.parseSchema(command.fieldSchemaJson());
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(idGenerator.nextId());
        type.setStoryBibleId(root.getStoryBibleId());
        type.setTypeCode(required(command.typeCode(), "typeCode").toUpperCase(Locale.ROOT));
        type.setSemanticFamily(Objects.requireNonNull(command.semanticFamily(), "semanticFamily"));
        type.setDisplayName(required(command.displayName(), "displayName"));
        type.setIconCode(command.iconCode());
        type.setFieldSchemaJson(defaultJson(command.fieldSchemaJson()));
        type.setSystem(false);
        type.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        requireOne(repository.insertNodeType(type), "Failed to create Story Bible node type");
        append(root, actorType, actorId, sourceRunId, "Created node type",
                draft("NODE_TYPE", type.getTypeId(), StoryBibleChangeOperation.CREATE, null, type));
        return type;
    }

    @Transactional
    public StoryBibleNodeType updateNodeType(Long projectId, Long typeId, StoryBibleCommands.UpdateNodeType command, Long actorId) {
        return updateNodeType(projectId, typeId, command, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public StoryBibleNodeType updateNodeType(Long projectId, Long typeId, StoryBibleCommands.UpdateNodeType command,
                                             StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleNodeType type = requireNodeType(root, typeId);
        if (Boolean.TRUE.equals(type.getSystem())) {
            throw BusinessException.conflict("System node types cannot be structurally edited");
        }
        String before = json(type);
        schemaValidator.parseSchema(command.fieldSchemaJson());
        type.setDisplayName(required(command.displayName(), "displayName"));
        type.setIconCode(command.iconCode());
        type.setFieldSchemaJson(defaultJson(command.fieldSchemaJson()));
        type.setSortOrder(command.sortOrder() == null ? type.getSortOrder() : command.sortOrder());
        requireOne(repository.updateNodeType(type), "Failed to update Story Bible node type");
        append(root, actorType, actorId, sourceRunId, "Updated node type",
                draftsWithBeforeJson("NODE_TYPE", typeId, StoryBibleChangeOperation.UPDATE, before, type));
        return type;
    }

    @Transactional
    public void archiveNodeType(Long projectId, Long typeId, Long actorId) {
        archiveNodeType(projectId, typeId, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public void archiveNodeType(Long projectId, Long typeId, StoryBibleActorType actorType,
                                Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleNodeType type = requireNodeType(root, typeId);
        if (Boolean.TRUE.equals(type.getSystem())) {
            throw BusinessException.conflict("System node types cannot be archived");
        }
        if (!repository.findNodes(root.getStoryBibleId(), typeId, null, null).isEmpty()) {
            throw BusinessException.conflict("Node type is still used by active Story Bible nodes");
        }
        requireOne(repository.archiveNodeType(root.getStoryBibleId(), typeId), "Failed to archive Story Bible node type");
        append(root, actorType, actorId, sourceRunId, "Archived node type",
                draft("NODE_TYPE", typeId, StoryBibleChangeOperation.ARCHIVE, type, null));
    }

    public List<StoryBibleNode> listNodes(Long projectId, Long typeId, StoryBibleCanonStatus status, String query) {
        StoryBible root = get(projectId);
        return repository.findNodes(root.getStoryBibleId(), typeId, status == null ? null : status.name(), normalizeQuery(query));
    }

    public List<StoryBibleNode> searchNodes(Long projectId, Long typeId, StoryBibleCanonStatus status,
                                            String query, Long categoryId, Long tagId, int limit) {
        StoryBible root = get(projectId);
        if (categoryId != null) requireCategory(root, categoryId);
        if (tagId != null) requireTag(root, tagId);
        return repository.findNodesFiltered(root.getStoryBibleId(), typeId,
                status == null ? null : status.name(), normalizeQuery(query), categoryId, tagId,
                Math.max(1, Math.min(limit, 500)));
    }

    public StoryBibleNode getNode(Long projectId, Long nodeId) {
        return requireNode(get(projectId), nodeId);
    }

    public NodeDetails getNodeDetails(Long projectId, Long nodeId) {
        StoryBible root = get(projectId);
        StoryBibleNode node = requireNode(root, nodeId);
        return new NodeDetails(
                node,
                repository.findAliases(root.getStoryBibleId(), nodeId),
                repository.findNodeCategories(root.getStoryBibleId(), nodeId).stream()
                        .map(StoryBibleNodeCategory::getCategoryId).toList(),
                repository.findNodeTags(root.getStoryBibleId(), nodeId).stream()
                        .map(StoryBibleNodeTag::getTagId).toList()
        );
    }

    public StoryBibleEffectiveStateResolver.EffectiveState getEffectiveState(Long projectId, Long nodeId, Long chapterId) {
        StoryBible root = get(projectId);
        StoryBibleNode node = requireNode(root, nodeId);
        StoryBibleNodeType nodeType = requireNodeType(root, node.getTypeId());
        return effectiveStateResolver.resolve(
                projectId,
                chapterId,
                node,
                nodeType,
                repository.findProgressions(root.getStoryBibleId(), List.of(nodeId))
        );
    }

    @Transactional
    public StoryBibleNode createNode(Long projectId, StoryBibleCommands.CreateNode command, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleNodeType nodeType = requireNodeType(root, command.typeId());
        schemaValidator.validateAttributes(command.attributesJson(), nodeType.getFieldSchemaJson());
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(idGenerator.nextId());
        node.setStoryBibleId(root.getStoryBibleId());
        node.setTypeId(command.typeId());
        node.setTitle(required(command.title(), "title"));
        node.setSummary(command.summary());
        node.setBodyMarkdown(command.bodyMarkdown());
        node.setAttributesJson(defaultJson(command.attributesJson()));
        node.setInclusionPolicy(command.inclusionPolicy() == null ? StoryBibleInclusionPolicy.AUTO_RETRIEVE : command.inclusionPolicy());
        node.setCanonStatus(command.canonStatus() == null ? StoryBibleCanonStatus.DRAFT : command.canonStatus());
        node.setRevision(1L);
        node.setCreatedBy(actorId);
        node.setUpdatedBy(actorId);
        requireOne(repository.insertNode(node), "Failed to create Story Bible node");
        replaceNodeOrganization(root, node.getNodeId(), command.aliases(), command.categoryIds(), command.tagIds());
        append(root, actorType, actorId, sourceRunId, "Created Story Bible node",
                draft("NODE", node.getNodeId(), StoryBibleChangeOperation.CREATE, null, node));
        return node;
    }

    @Transactional
    public StoryBibleNode updateNode(Long projectId, Long nodeId, StoryBibleCommands.UpdateNode command, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleNode node = requireNode(root, nodeId);
        StoryBibleNodeType nodeType = requireNodeType(root, command.typeId());
        schemaValidator.validateAttributes(command.attributesJson(), nodeType.getFieldSchemaJson());
        String before = json(node);
        node.setTypeId(command.typeId());
        node.setTitle(required(command.title(), "title"));
        node.setSummary(command.summary());
        node.setBodyMarkdown(command.bodyMarkdown());
        node.setAttributesJson(defaultJson(command.attributesJson()));
        node.setInclusionPolicy(command.inclusionPolicy() == null ? node.getInclusionPolicy() : command.inclusionPolicy());
        node.setCanonStatus(command.canonStatus() == null ? node.getCanonStatus() : command.canonStatus());
        node.setUpdatedBy(actorId);
        requireOne(repository.updateNode(node, command.expectedRevision()), "Story Bible node revision conflict");
        node.setRevision(command.expectedRevision() + 1);
        replaceNodeOrganization(root, nodeId, command.aliases(), command.categoryIds(), command.tagIds());
        append(root, actorType, actorId, sourceRunId, "Updated Story Bible node",
                draftsWithBeforeJson("NODE", nodeId, StoryBibleChangeOperation.UPDATE, before, node));
        return node;
    }

    @Transactional
    public void deleteNode(Long projectId, Long nodeId, Long expectedRevision, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleNode node = requireNode(root, nodeId);
        requireOne(repository.softDeleteNode(root.getStoryBibleId(), nodeId, expectedRevision, actorId), "Story Bible node revision conflict");
        append(root, actorType, actorId, sourceRunId, "Deleted Story Bible node",
                draft("NODE", nodeId, StoryBibleChangeOperation.DELETE, node, null));
    }

    public List<StoryBibleCategory> listCategories(Long projectId) {
        return repository.findCategories(get(projectId).getStoryBibleId());
    }

    @Transactional
    public StoryBibleCategory createCategory(Long projectId, StoryBibleCommands.CreateCategory command, Long actorId) {
        return createCategory(projectId, command, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public StoryBibleCategory createCategory(Long projectId, StoryBibleCommands.CreateCategory command,
                                              StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleCategory category = new StoryBibleCategory();
        category.setCategoryId(idGenerator.nextId());
        category.setStoryBibleId(root.getStoryBibleId());
        validateCategoryParent(root, null, command.parentCategoryId());
        category.setParentCategoryId(command.parentCategoryId());
        category.setName(required(command.name(), "name"));
        category.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        requireOne(repository.insertCategory(category), "Failed to create Story Bible category");
        append(root, actorType, actorId, sourceRunId, "Created category",
                draft("CATEGORY", category.getCategoryId(), StoryBibleChangeOperation.CREATE, null, category));
        return category;
    }

    @Transactional
    public StoryBibleCategory updateCategory(Long projectId, Long categoryId, StoryBibleCommands.UpdateCategory command, Long actorId) {
        return updateCategory(projectId, categoryId, command, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public StoryBibleCategory updateCategory(Long projectId, Long categoryId, StoryBibleCommands.UpdateCategory command,
                                              StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleCategory category = repository.findCategories(root.getStoryBibleId()).stream()
                .filter(item -> Objects.equals(item.getCategoryId(), categoryId)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("Story Bible category not found"));
        String before = json(category);
        validateCategoryParent(root, categoryId, command.parentCategoryId());
        category.setParentCategoryId(command.parentCategoryId());
        category.setName(required(command.name(), "name"));
        category.setSortOrder(command.sortOrder() == null ? category.getSortOrder() : command.sortOrder());
        requireOne(repository.updateCategory(category), "Failed to update Story Bible category");
        append(root, actorType, actorId, sourceRunId, "Updated category",
                draftsWithBeforeJson("CATEGORY", categoryId, StoryBibleChangeOperation.UPDATE, before, category));
        return category;
    }

    @Transactional
    public void deleteCategory(Long projectId, Long categoryId, Long actorId) {
        deleteCategory(projectId, categoryId, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public void deleteCategory(Long projectId, Long categoryId, StoryBibleActorType actorType,
                               Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleCategory category = requireCategory(root, categoryId);
        if (repository.findCategories(root.getStoryBibleId()).stream()
                .anyMatch(item -> Objects.equals(item.getParentCategoryId(), categoryId))) {
            throw BusinessException.conflict("Category still has child categories");
        }
        requireOne(repository.softDeleteCategory(root.getStoryBibleId(), categoryId), "Failed to delete Story Bible category");
        append(root, actorType, actorId, sourceRunId, "Deleted category",
                draft("CATEGORY", categoryId, StoryBibleChangeOperation.DELETE, category, null));
    }

    public List<StoryBibleTag> listTags(Long projectId) {
        return repository.findTags(get(projectId).getStoryBibleId());
    }

    @Transactional
    public StoryBibleTag createTag(Long projectId, StoryBibleCommands.CreateTag command, Long actorId) {
        return createTag(projectId, command, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public StoryBibleTag createTag(Long projectId, StoryBibleCommands.CreateTag command,
                                    StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleTag tag = new StoryBibleTag();
        tag.setTagId(idGenerator.nextId());
        tag.setStoryBibleId(root.getStoryBibleId());
        tag.setName(required(command.name(), "name"));
        tag.setNormalizedName(normalizeAlias(command.name()));
        tag.setColor(command.color());
        requireOne(repository.insertTag(tag), "Failed to create Story Bible tag");
        append(root, actorType, actorId, sourceRunId, "Created tag",
                draft("TAG", tag.getTagId(), StoryBibleChangeOperation.CREATE, null, tag));
        return tag;
    }

    @Transactional
    public StoryBibleTag updateTag(Long projectId, Long tagId, StoryBibleCommands.UpdateTag command, Long actorId) {
        return updateTag(projectId, tagId, command, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public StoryBibleTag updateTag(Long projectId, Long tagId, StoryBibleCommands.UpdateTag command,
                                    StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleTag tag = requireTag(root, tagId);
        String before = json(tag);
        tag.setName(required(command.name(), "name"));
        tag.setNormalizedName(normalizeAlias(command.name()));
        tag.setColor(command.color());
        requireOne(repository.updateTag(tag), "Failed to update Story Bible tag");
        append(root, actorType, actorId, sourceRunId, "Updated tag",
                draftsWithBeforeJson("TAG", tagId, StoryBibleChangeOperation.UPDATE, before, tag));
        return tag;
    }

    @Transactional
    public void deleteTag(Long projectId, Long tagId, Long actorId) {
        deleteTag(projectId, tagId, StoryBibleActorType.USER, actorId, null);
    }

    @Transactional
    public void deleteTag(Long projectId, Long tagId, StoryBibleActorType actorType,
                          Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleTag tag = requireTag(root, tagId);
        requireOne(repository.softDeleteTag(root.getStoryBibleId(), tagId), "Failed to delete Story Bible tag");
        append(root, actorType, actorId, sourceRunId, "Deleted tag",
                draft("TAG", tagId, StoryBibleChangeOperation.DELETE, tag, null));
    }

    public List<StoryBibleRelation> listRelations(Long projectId, List<Long> nodeIds) {
        return repository.findRelations(get(projectId).getStoryBibleId(), nodeIds);
    }

    @Transactional
    public StoryBibleRelation createRelation(Long projectId, StoryBibleCommands.CreateRelation command, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        requireNode(root, command.sourceNodeId());
        requireNode(root, command.targetNodeId());
        parseJson(command.attributesJson(), "attributesJson");
        StoryBibleRelation relation = new StoryBibleRelation();
        relation.setRelationId(idGenerator.nextId());
        relation.setStoryBibleId(root.getStoryBibleId());
        relation.setSourceNodeId(command.sourceNodeId());
        relation.setRelationType(required(command.relationType(), "relationType").toUpperCase(Locale.ROOT));
        relation.setTargetNodeId(command.targetNodeId());
        relation.setDescription(command.description());
        relation.setAttributesJson(defaultJson(command.attributesJson()));
        relation.setRevision(1L);
        relation.setCreatedBy(actorId);
        relation.setUpdatedBy(actorId);
        requireOne(repository.insertRelation(relation), "Failed to create Story Bible relation");
        append(root, actorType, actorId, sourceRunId, "Created Story Bible relation",
                draft("RELATION", relation.getRelationId(), StoryBibleChangeOperation.CREATE, null, relation));
        return relation;
    }

    @Transactional
    public StoryBibleRelation updateRelation(Long projectId, Long relationId, StoryBibleCommands.UpdateRelation command, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleRelation relation = requireRelation(root, relationId);
        requireNode(root, command.targetNodeId());
        parseJson(command.attributesJson(), "attributesJson");
        String before = json(relation);
        relation.setRelationType(required(command.relationType(), "relationType").toUpperCase(Locale.ROOT));
        relation.setTargetNodeId(command.targetNodeId());
        relation.setDescription(command.description());
        relation.setAttributesJson(defaultJson(command.attributesJson()));
        relation.setUpdatedBy(actorId);
        requireOne(repository.updateRelation(relation, command.expectedRevision()), "Story Bible relation revision conflict");
        relation.setRevision(command.expectedRevision() + 1);
        append(root, actorType, actorId, sourceRunId, "Updated Story Bible relation",
                draftsWithBeforeJson("RELATION", relationId, StoryBibleChangeOperation.UPDATE, before, relation));
        return relation;
    }

    @Transactional
    public void deleteRelation(Long projectId, Long relationId, Long expectedRevision, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleRelation relation = requireRelation(root, relationId);
        requireOne(repository.softDeleteRelation(root.getStoryBibleId(), relationId, expectedRevision, actorId), "Story Bible relation revision conflict");
        append(root, actorType, actorId, sourceRunId, "Deleted Story Bible relation",
                draft("RELATION", relationId, StoryBibleChangeOperation.DELETE, relation, null));
    }

    public List<StoryBibleProgression> listProgressions(Long projectId, List<Long> nodeIds) {
        return repository.findProgressions(get(projectId).getStoryBibleId(), nodeIds);
    }

    @Transactional
    public StoryBibleProgression createProgression(Long projectId, StoryBibleCommands.CreateProgression command, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleNode node = requireNode(root, command.nodeId());
        StoryBibleNodeType nodeType = requireNodeType(root, node.getTypeId());
        patchValidator.validate(command.patchJson(), nodeType.getFieldSchemaJson());
        StoryBibleProgression progression = new StoryBibleProgression();
        progression.setProgressionId(idGenerator.nextId());
        progression.setStoryBibleId(root.getStoryBibleId());
        progression.setNodeId(command.nodeId());
        progression.setAnchorChapterId(Objects.requireNonNull(command.anchorChapterId(), "anchorChapterId"));
        progression.setEndChapterId(command.endChapterId());
        progression.setStoryEventNodeId(command.storyEventNodeId());
        progression.setPatchJson(command.patchJson());
        progression.setSummary(command.summary());
        progression.setRevision(1L);
        progression.setCreatedBy(actorId);
        progression.setUpdatedBy(actorId);
        requireOne(repository.insertProgression(progression), "Failed to create Story Bible progression");
        append(root, actorType, actorId, sourceRunId, "Created Story Bible progression",
                draft("PROGRESSION", progression.getProgressionId(), StoryBibleChangeOperation.CREATE, null, progression));
        return progression;
    }

    @Transactional
    public StoryBibleProgression updateProgression(Long projectId, Long progressionId, StoryBibleCommands.UpdateProgression command, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleProgression progression = requireProgression(root, progressionId);
        StoryBibleNode node = requireNode(root, progression.getNodeId());
        StoryBibleNodeType nodeType = requireNodeType(root, node.getTypeId());
        patchValidator.validate(command.patchJson(), nodeType.getFieldSchemaJson());
        String before = json(progression);
        progression.setAnchorChapterId(Objects.requireNonNull(command.anchorChapterId(), "anchorChapterId"));
        progression.setEndChapterId(command.endChapterId());
        progression.setStoryEventNodeId(command.storyEventNodeId());
        progression.setPatchJson(command.patchJson());
        progression.setSummary(command.summary());
        progression.setUpdatedBy(actorId);
        requireOne(repository.updateProgression(progression, command.expectedRevision()), "Story Bible progression revision conflict");
        progression.setRevision(command.expectedRevision() + 1);
        append(root, actorType, actorId, sourceRunId, "Updated Story Bible progression",
                draftsWithBeforeJson("PROGRESSION", progressionId, StoryBibleChangeOperation.UPDATE, before, progression));
        return progression;
    }

    @Transactional
    public void deleteProgression(Long projectId, Long progressionId, Long expectedRevision, StoryBibleActorType actorType, Long actorId, Long sourceRunId) {
        StoryBible root = get(projectId);
        StoryBibleProgression progression = requireProgression(root, progressionId);
        requireOne(repository.softDeleteProgression(root.getStoryBibleId(), progressionId, expectedRevision, actorId), "Story Bible progression revision conflict");
        append(root, actorType, actorId, sourceRunId, "Deleted Story Bible progression",
                draft("PROGRESSION", progressionId, StoryBibleChangeOperation.DELETE, progression, null));
    }

    public List<StoryBibleViewPreference> listViewPreferences(Long projectId) {
        return repository.findViewPreferences(get(projectId).getStoryBibleId());
    }

    @Transactional
    public StoryBibleViewPreference updateViewPreference(Long projectId, StoryBibleCommands.UpdateViewPreference command, Long actorId) {
        StoryBible root = get(projectId);
        StoryBibleViewPreference preference = new StoryBibleViewPreference();
        preference.setStoryBibleId(root.getStoryBibleId());
        preference.setViewCode(required(command.viewCode(), "viewCode").toUpperCase(Locale.ROOT));
        preference.setDisplayName(required(command.displayName(), "displayName"));
        preference.setHidden(Boolean.TRUE.equals(command.hidden()));
        preference.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        preference.setUpdatedBy(actorId);
        requireOne(repository.upsertViewPreference(preference), "Failed to update Story Bible view");
        append(root, actorId, "Updated Story Bible view", draft("VIEW", root.getStoryBibleId(), StoryBibleChangeOperation.UPDATE, null, preference));
        return preference;
    }

    public List<StoryBibleChangeset> recentChanges(Long projectId, int limit) {
        return repository.findRecentChangesets(get(projectId).getStoryBibleId(), Math.max(1, Math.min(limit, 200)));
    }

    public ChangesetDetails getChangeset(Long projectId, Long changesetId) {
        StoryBible root = get(projectId);
        StoryBibleChangeset changeset = repository.findChangeset(root.getStoryBibleId(), changesetId);
        if (changeset == null) throw BusinessException.notFound("Story Bible changeset not found");
        return new ChangesetDetails(changeset,
                repository.findChangeItemsByChangesetIds(List.of(changeset.getChangesetId())));
    }

    public List<StoryBibleChangeset> nodeChanges(Long projectId, Long nodeId, int limit) {
        StoryBible root = get(projectId);
        requireNode(root, nodeId);
        return repository.findChangesetsForNode(root.getStoryBibleId(), nodeId,
                Math.max(1, Math.min(limit, 200)));
    }

    private void replaceNodeOrganization(StoryBible root, Long nodeId, List<String> aliases, List<Long> categoryIds, List<Long> tagIds) {
        for (StoryBibleAlias existing : repository.findAliases(root.getStoryBibleId(), nodeId)) {
            repository.softDeleteAlias(root.getStoryBibleId(), existing.getAliasId());
        }
        for (String value : aliases == null ? List.<String>of() : aliases) {
            if (value == null || value.isBlank()) continue;
            StoryBibleAlias alias = new StoryBibleAlias();
            alias.setAliasId(idGenerator.nextId());
            alias.setStoryBibleId(root.getStoryBibleId());
            alias.setNodeId(nodeId);
            alias.setAlias(value.trim());
            alias.setNormalizedAlias(normalizeAlias(value));
            requireOne(repository.insertAlias(alias), "Failed to save Story Bible alias");
        }
        repository.deleteNodeCategories(root.getStoryBibleId(), nodeId);
        for (Long categoryId : categoryIds == null ? List.<Long>of() : categoryIds) {
            StoryBibleNodeCategory membership = new StoryBibleNodeCategory();
            membership.setStoryBibleId(root.getStoryBibleId());
            membership.setNodeId(nodeId);
            membership.setCategoryId(categoryId);
            requireOne(repository.insertNodeCategory(membership), "Failed to save Story Bible category membership");
        }
        repository.deleteNodeTags(root.getStoryBibleId(), nodeId);
        for (Long tagId : tagIds == null ? List.<Long>of() : tagIds) {
            StoryBibleNodeTag membership = new StoryBibleNodeTag();
            membership.setStoryBibleId(root.getStoryBibleId());
            membership.setNodeId(nodeId);
            membership.setTagId(tagId);
            requireOne(repository.insertNodeTag(membership), "Failed to save Story Bible tag membership");
        }
    }

    private StoryBibleNode requireNode(StoryBible root, Long nodeId) {
        StoryBibleNode node = repository.findNode(root.getStoryBibleId(), nodeId);
        if (node == null) throw BusinessException.notFound("Story Bible node not found");
        return node;
    }

    private StoryBibleNodeType requireNodeType(StoryBible root, Long typeId) {
        StoryBibleNodeType type = repository.findNodeType(root.getStoryBibleId(), typeId);
        if (type == null) throw BusinessException.notFound("Story Bible node type not found");
        return type;
    }

    private StoryBibleCategory requireCategory(StoryBible root, Long categoryId) {
        return repository.findCategories(root.getStoryBibleId()).stream()
                .filter(item -> Objects.equals(item.getCategoryId(), categoryId))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("Story Bible category not found"));
    }

    private StoryBibleTag requireTag(StoryBible root, Long tagId) {
        return repository.findTags(root.getStoryBibleId()).stream()
                .filter(item -> Objects.equals(item.getTagId(), tagId))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("Story Bible tag not found"));
    }

    private void validateCategoryParent(StoryBible root, Long categoryId, Long parentCategoryId) {
        if (parentCategoryId == null) return;
        if (Objects.equals(categoryId, parentCategoryId)) {
            throw BusinessException.badRequest("Category cannot be its own parent");
        }
        Map<Long, StoryBibleCategory> categories = new HashMap<>();
        for (StoryBibleCategory item : repository.findCategories(root.getStoryBibleId())) {
            categories.put(item.getCategoryId(), item);
        }
        if (!categories.containsKey(parentCategoryId)) {
            throw BusinessException.notFound("Parent Story Bible category not found");
        }
        Long cursor = parentCategoryId;
        while (cursor != null) {
            if (Objects.equals(cursor, categoryId)) {
                throw BusinessException.badRequest("Category hierarchy cannot contain a cycle");
            }
            StoryBibleCategory current = categories.get(cursor);
            cursor = current == null ? null : current.getParentCategoryId();
        }
    }

    private StoryBibleRelation requireRelation(StoryBible root, Long relationId) {
        StoryBibleRelation relation = repository.findRelation(root.getStoryBibleId(), relationId);
        if (relation == null) throw BusinessException.notFound("Story Bible relation not found");
        return relation;
    }

    private StoryBibleProgression requireProgression(StoryBible root, Long progressionId) {
        StoryBibleProgression progression = repository.findProgression(root.getStoryBibleId(), progressionId);
        if (progression == null) throw BusinessException.notFound("Story Bible progression not found");
        return progression;
    }

    private void append(StoryBible root, Long actorId, String summary, ChangeDraft draft) {
        append(root, StoryBibleActorType.USER, actorId, null, summary, draft);
    }

    private void append(StoryBible root, StoryBibleActorType actorType, Long actorId, Long sourceRunId, String summary, ChangeDraft draft) {
        changesetService.append(root, actorType, actorId, sourceRunId, summary, List.of(draft));
    }

    private void append(StoryBible root, StoryBibleActorType actorType, Long actorId, Long sourceRunId, String summary, List<ChangeDraft> drafts) {
        changesetService.append(root, actorType, actorId, sourceRunId, summary, drafts);
    }

    private ChangeDraft draft(String entityType, Long entityId, StoryBibleChangeOperation operation, Object before, Object after) {
        return new ChangeDraft(entityType, entityId, operation, "/", before == null ? null : asJson(before), after == null ? null : asJson(after));
    }

    private List<ChangeDraft> draftsWithBeforeJson(String entityType, Long entityId, StoryBibleChangeOperation operation, String beforeJson, Object after) {
        try {
            var before = objectMapper.readTree(beforeJson);
            var afterNode = objectMapper.valueToTree(after);
            List<ChangeDraft> drafts = new ArrayList<>();
            var fields = new java.util.TreeSet<String>();
            before.fieldNames().forEachRemaining(fields::add);
            afterNode.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                var beforeValue = before.get(field);
                var afterValue = afterNode.get(field);
                if (!Objects.equals(beforeValue, afterValue)) {
                    drafts.add(new ChangeDraft(entityType, entityId, operation, "/" + escapePointer(field),
                            beforeValue == null ? null : beforeValue.toString(),
                            afterValue == null ? null : afterValue.toString()));
                }
            }
            return drafts.isEmpty()
                    ? List.of(new ChangeDraft(entityType, entityId, operation, "/", beforeJson, asJson(after)))
                    : List.copyOf(drafts);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("Story Bible value cannot be compared");
        }
    }

    private String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private String json(Object value) {
        return asJson(value);
    }

    private String asJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("Story Bible value cannot be serialized");
        }
    }

    private void parseJson(String value, String field) {
        try {
            objectMapper.readTree(defaultJson(value));
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest(field + " must be valid JSON");
        }
    }

    private String defaultJson(String value) {
        return value == null || value.isBlank() ? EMPTY_OBJECT : value.trim();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest(field + " is required");
        return value.trim();
    }

    private String normalizeAlias(String value) {
        return Normalizer.normalize(required(value, "alias"), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private String normalizeQuery(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) {
            if (message.toLowerCase(Locale.ROOT).contains("revision conflict")) throw BusinessException.conflict(message);
            throw BusinessException.of(message);
        }
    }

    private String defaultFamilyName(StoryBibleSemanticFamily family) {
        return switch (family) {
            case CORE -> "Story Core";
            case CHARACTER -> "Characters";
            case WORLD -> "World";
            case THING -> "Things";
            case NARRATIVE -> "Narrative";
            case TIMELINE -> "Timeline";
        };
    }

    private List<SystemNodeType> systemNodeTypes() {
        return List.of(
                new SystemNodeType("STORY_CORE", StoryBibleSemanticFamily.CORE, "Story Core", "book-open", 10),
                new SystemNodeType("CHARACTER", StoryBibleSemanticFamily.CHARACTER, "Character", "user", 20),
                new SystemNodeType("CHARACTER_ARC", StoryBibleSemanticFamily.CHARACTER, "Character Arc", "route", 30),
                new SystemNodeType("LOCATION", StoryBibleSemanticFamily.WORLD, "Location", "map-pin", 40),
                new SystemNodeType("ORGANIZATION", StoryBibleSemanticFamily.WORLD, "Organization", "building", 50),
                new SystemNodeType("FACTION", StoryBibleSemanticFamily.WORLD, "Faction", "shield", 60),
                new SystemNodeType("MAGIC_SYSTEM", StoryBibleSemanticFamily.WORLD, "Magic System", "sparkles", 70),
                new SystemNodeType("ITEM", StoryBibleSemanticFamily.THING, "Item", "gem", 80),
                new SystemNodeType("ABILITY", StoryBibleSemanticFamily.THING, "Ability", "zap", 90),
                new SystemNodeType("TECHNOLOGY", StoryBibleSemanticFamily.THING, "Technology", "cpu", 100),
                new SystemNodeType("TERM", StoryBibleSemanticFamily.THING, "Term", "text", 110),
                new SystemNodeType("PLOTLINE", StoryBibleSemanticFamily.NARRATIVE, "Plotline", "git-branch", 120),
                new SystemNodeType("MYSTERY", StoryBibleSemanticFamily.NARRATIVE, "Mystery", "circle-help", 130),
                new SystemNodeType("FORESHADOWING", StoryBibleSemanticFamily.NARRATIVE, "Foreshadowing", "eye", 140),
                new SystemNodeType("EVENT", StoryBibleSemanticFamily.TIMELINE, "Event", "calendar", 150),
                new SystemNodeType("FACT", StoryBibleSemanticFamily.TIMELINE, "Fact", "check", 160),
                new SystemNodeType("CONTINUITY_CONSTRAINT", StoryBibleSemanticFamily.TIMELINE, "Continuity Constraint", "lock", 170)
        );
    }

    private record SystemNodeType(String code, StoryBibleSemanticFamily family, String displayName, String iconCode, int sortOrder) {
    }

    public record NodeDetails(
            StoryBibleNode node,
            List<StoryBibleAlias> aliases,
            List<Long> categoryIds,
            List<Long> tagIds
    ) {
    }

    public record ChangesetDetails(
            StoryBibleChangeset changeset,
            List<com.penmate.backend.domain.storybible.model.StoryBibleChangeItem> items
    ) {
        public ChangesetDetails {
            items = List.copyOf(items == null ? List.of() : items);
        }
    }
}
