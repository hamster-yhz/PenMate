package com.penmate.backend.infrastructure.persistence.storybible;

import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeTag;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.model.StoryBibleTag;
import com.penmate.backend.domain.storybible.model.StoryBibleViewPreference;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Repository
public class StoryBibleRepositoryImpl implements StoryBibleRepository {

    private final StoryBibleMapper mapper;

    public StoryBibleRepositoryImpl(StoryBibleMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override public StoryBible findByProjectId(Long projectId) { return mapper.findByProjectId(projectId); }
    @Override public int insertStoryBible(StoryBible storyBible) { return mapper.insertStoryBible(storyBible); }
    @Override public int incrementContentRevision(Long storyBibleId, Long expectedRevision) { return mapper.incrementContentRevision(storyBibleId, expectedRevision); }
    @Override public List<StoryBibleNodeType> findNodeTypes(Long storyBibleId) { return mapper.findNodeTypes(storyBibleId); }
    @Override public StoryBibleNodeType findNodeType(Long storyBibleId, Long typeId) { return mapper.findNodeType(storyBibleId, typeId); }
    @Override public int insertNodeType(StoryBibleNodeType nodeType) { return mapper.insertNodeType(nodeType); }
    @Override public int updateNodeType(StoryBibleNodeType nodeType) { return mapper.updateNodeType(nodeType); }
    @Override public int archiveNodeType(Long storyBibleId, Long typeId) { return mapper.archiveNodeType(storyBibleId, typeId); }
    @Override public List<StoryBibleNode> findNodes(Long storyBibleId, Long typeId, String canonStatus, String query) { return mapper.findNodes(storyBibleId, typeId, canonStatus, query); }
    @Override public List<StoryBibleNode> findNodesFiltered(Long storyBibleId, Long typeId, String canonStatus, String query, Long categoryId, Long tagId, int limit) { return mapper.findNodesFiltered(storyBibleId, typeId, canonStatus, query, categoryId, tagId, limit); }
    @Override public List<StoryBibleNode> searchNodesLexically(Long storyBibleId, List<String> terms, int limit) { return mapper.searchNodesLexically(storyBibleId, terms, limit); }
    @Override public List<StoryBibleNode> findAlwaysIncludeNodes(Long storyBibleId) { return mapper.findAlwaysIncludeNodes(storyBibleId); }
    @Override public List<StoryBibleNode> findNodesByIds(Long storyBibleId, List<Long> nodeIds) { return empty(nodeIds) ? List.of() : mapper.findNodesByIds(storyBibleId, nodeIds); }
    @Override public StoryBibleNode findNode(Long storyBibleId, Long nodeId) { return mapper.findNode(storyBibleId, nodeId); }
    @Override public int insertNode(StoryBibleNode node) { return mapper.insertNode(node); }
    @Override public int updateNode(StoryBibleNode node, Long expectedRevision) { return mapper.updateNode(node, expectedRevision); }
    @Override public int softDeleteNode(Long storyBibleId, Long nodeId, Long expectedRevision, Long updatedBy) { return mapper.softDeleteNode(storyBibleId, nodeId, expectedRevision, updatedBy); }
    @Override public List<StoryBibleAlias> findAliases(Long storyBibleId, Long nodeId) { return mapper.findAliases(storyBibleId, nodeId); }
    @Override public List<StoryBibleAlias> findAliasesByNodeIds(Long storyBibleId, List<Long> nodeIds) { return empty(nodeIds) ? List.of() : mapper.findAliasesByNodeIds(storyBibleId, nodeIds); }
    @Override public List<StoryBibleAlias> findByNormalizedAlias(Long storyBibleId, String normalizedAlias) { return mapper.findByNormalizedAlias(storyBibleId, normalizedAlias); }
    @Override public int insertAlias(StoryBibleAlias alias) { return mapper.insertAlias(alias); }
    @Override public int softDeleteAlias(Long storyBibleId, Long aliasId) { return mapper.softDeleteAlias(storyBibleId, aliasId); }
    @Override public List<StoryBibleCategory> findCategories(Long storyBibleId) { return mapper.findCategories(storyBibleId); }
    @Override public int insertCategory(StoryBibleCategory category) { return mapper.insertCategory(category); }
    @Override public int updateCategory(StoryBibleCategory category) { return mapper.updateCategory(category); }
    @Override public int softDeleteCategory(Long storyBibleId, Long categoryId) { return mapper.softDeleteCategory(storyBibleId, categoryId); }
    @Override public int insertNodeCategory(StoryBibleNodeCategory membership) { return mapper.insertNodeCategory(membership); }
    @Override public List<StoryBibleNodeCategory> findNodeCategories(Long storyBibleId, Long nodeId) { return mapper.findNodeCategories(storyBibleId, nodeId); }
    @Override public int deleteNodeCategories(Long storyBibleId, Long nodeId) { return mapper.deleteNodeCategories(storyBibleId, nodeId); }
    @Override public int deleteNodeCategoriesByCategory(Long storyBibleId, Long categoryId) { return mapper.deleteNodeCategoriesByCategory(storyBibleId, categoryId); }
    @Override public List<StoryBibleTag> findTags(Long storyBibleId) { return mapper.findTags(storyBibleId); }
    @Override public int insertTag(StoryBibleTag tag) { return mapper.insertTag(tag); }
    @Override public int updateTag(StoryBibleTag tag) { return mapper.updateTag(tag); }
    @Override public int softDeleteTag(Long storyBibleId, Long tagId) { return mapper.softDeleteTag(storyBibleId, tagId); }
    @Override public int insertNodeTag(StoryBibleNodeTag membership) { return mapper.insertNodeTag(membership); }
    @Override public List<StoryBibleNodeTag> findNodeTags(Long storyBibleId, Long nodeId) { return mapper.findNodeTags(storyBibleId, nodeId); }
    @Override public int deleteNodeTags(Long storyBibleId, Long nodeId) { return mapper.deleteNodeTags(storyBibleId, nodeId); }
    @Override public int deleteNodeTagsByTag(Long storyBibleId, Long tagId) { return mapper.deleteNodeTagsByTag(storyBibleId, tagId); }
    @Override public List<StoryBibleRelation> findRelations(Long storyBibleId, List<Long> nodeIds) { return mapper.findRelations(storyBibleId, nodeIds); }
    @Override public StoryBibleRelation findRelation(Long storyBibleId, Long relationId) { return mapper.findRelation(storyBibleId, relationId); }
    @Override public int insertRelation(StoryBibleRelation relation) { return mapper.insertRelation(relation); }
    @Override public int updateRelation(StoryBibleRelation relation, Long expectedRevision) { return mapper.updateRelation(relation, expectedRevision); }
    @Override public int softDeleteRelation(Long storyBibleId, Long relationId, Long expectedRevision, Long updatedBy) { return mapper.softDeleteRelation(storyBibleId, relationId, expectedRevision, updatedBy); }
    @Override public List<StoryBibleProgression> findProgressions(Long storyBibleId, List<Long> nodeIds) { return mapper.findProgressions(storyBibleId, nodeIds); }
    @Override public StoryBibleProgression findProgression(Long storyBibleId, Long progressionId) { return mapper.findProgression(storyBibleId, progressionId); }
    @Override public int insertProgression(StoryBibleProgression progression) { return mapper.insertProgression(progression); }
    @Override public int updateProgression(StoryBibleProgression progression, Long expectedRevision) { return mapper.updateProgression(progression, expectedRevision); }
    @Override public int softDeleteProgression(Long storyBibleId, Long progressionId, Long expectedRevision, Long updatedBy) { return mapper.softDeleteProgression(storyBibleId, progressionId, expectedRevision, updatedBy); }
    @Override public List<StoryBibleViewPreference> findViewPreferences(Long storyBibleId) { return mapper.findViewPreferences(storyBibleId); }
    @Override public int upsertViewPreference(StoryBibleViewPreference preference) { return mapper.upsertViewPreference(preference); }
    @Override public int insertChangeset(StoryBibleChangeset changeset) { return mapper.insertChangeset(changeset); }
    @Override public int insertChangeItem(StoryBibleChangeItem item) { return mapper.insertChangeItem(item); }
    @Override public List<StoryBibleChangeset> findRecentChangesets(Long storyBibleId, int limit) { return mapper.findRecentChangesets(storyBibleId, limit); }
    @Override public StoryBibleChangeset findChangeset(Long storyBibleId, Long changesetId) { return mapper.findChangeset(storyBibleId, changesetId); }
    @Override public List<StoryBibleChangeset> findChangesetsForNode(Long storyBibleId, Long nodeId, int limit) { return mapper.findChangesetsForNode(storyBibleId, nodeId, limit); }
    @Override public List<StoryBibleChangeset> findChangesetsBefore(Long storyBibleId, LocalDateTime cutoff, int retainCount) { return mapper.findChangesetsBefore(storyBibleId, cutoff, retainCount); }
    @Override public List<StoryBible> findStoryBiblesWithChangesetsBefore(LocalDateTime cutoff) { return mapper.findStoryBiblesWithChangesetsBefore(cutoff); }
    @Override public List<StoryBibleChangeItem> findChangeItemsByChangesetIds(List<Long> changesetIds) { return empty(changesetIds) ? List.of() : mapper.findChangeItemsByChangesetIds(changesetIds); }
    @Override public int deleteChangeItemsByChangesetIds(List<Long> changesetIds) { return empty(changesetIds) ? 0 : mapper.deleteChangeItemsByChangesetIds(changesetIds); }
    @Override public int deleteChangesetsByIds(Long storyBibleId, List<Long> changesetIds) { return empty(changesetIds) ? 0 : mapper.deleteChangesetsByIds(storyBibleId, changesetIds); }

    private boolean empty(List<Long> ids) {
        return ids == null || ids.isEmpty();
    }
}
