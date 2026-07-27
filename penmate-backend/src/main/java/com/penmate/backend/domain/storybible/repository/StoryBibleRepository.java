package com.penmate.backend.domain.storybible.repository;

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

import java.time.Instant;
import java.util.List;

public interface StoryBibleRepository {

    StoryBible findByProjectId(Long projectId);

    int insertStoryBible(StoryBible storyBible);

    int incrementContentRevision(Long storyBibleId, Long expectedRevision);

    List<StoryBibleNodeType> findNodeTypes(Long storyBibleId);

    StoryBibleNodeType findNodeType(Long storyBibleId, Long typeId);

    int insertNodeType(StoryBibleNodeType nodeType);

    int updateNodeType(StoryBibleNodeType nodeType);

    int archiveNodeType(Long storyBibleId, Long typeId);

    List<StoryBibleNode> findNodes(Long storyBibleId, Long typeId, String canonStatus, String query);

    List<StoryBibleNode> findNodesFiltered(Long storyBibleId, Long typeId, String canonStatus, String query,
                                           Long categoryId, Long tagId, int limit);

    List<StoryBibleNode> searchNodesLexically(Long storyBibleId, List<String> terms, int limit);

    List<StoryBibleNode> findAlwaysIncludeNodes(Long storyBibleId);

    List<StoryBibleNode> findNodesByIds(Long storyBibleId, List<Long> nodeIds);

    StoryBibleNode findNode(Long storyBibleId, Long nodeId);

    int insertNode(StoryBibleNode node);

    int updateNode(StoryBibleNode node, Long expectedRevision);

    int softDeleteNode(Long storyBibleId, Long nodeId, Long expectedRevision, Long updatedBy);

    List<StoryBibleAlias> findAliases(Long storyBibleId, Long nodeId);

    List<StoryBibleAlias> findAliasesByNodeIds(Long storyBibleId, List<Long> nodeIds);

    List<StoryBibleAlias> findByNormalizedAlias(Long storyBibleId, String normalizedAlias);

    int insertAlias(StoryBibleAlias alias);

    int softDeleteAlias(Long storyBibleId, Long aliasId);

    List<StoryBibleCategory> findCategories(Long storyBibleId);

    int insertCategory(StoryBibleCategory category);

    int updateCategory(StoryBibleCategory category);

    int softDeleteCategory(Long storyBibleId, Long categoryId);

    int insertNodeCategory(StoryBibleNodeCategory membership);

    List<StoryBibleNodeCategory> findNodeCategories(Long storyBibleId, Long nodeId);

    List<StoryBibleNodeCategory> findNodeCategoriesByCategory(Long storyBibleId, Long categoryId);

    int deleteNodeCategories(Long storyBibleId, Long nodeId);

    int deleteNodeCategoriesByCategory(Long storyBibleId, Long categoryId);

    List<StoryBibleTag> findTags(Long storyBibleId);

    int insertTag(StoryBibleTag tag);

    int updateTag(StoryBibleTag tag);

    int softDeleteTag(Long storyBibleId, Long tagId);

    int insertNodeTag(StoryBibleNodeTag membership);

    List<StoryBibleNodeTag> findNodeTags(Long storyBibleId, Long nodeId);

    List<StoryBibleNodeTag> findNodeTagsByTag(Long storyBibleId, Long tagId);

    int deleteNodeTags(Long storyBibleId, Long nodeId);

    int deleteNodeTagsByTag(Long storyBibleId, Long tagId);

    List<StoryBibleRelation> findRelations(Long storyBibleId, List<Long> nodeIds);

    StoryBibleRelation findRelation(Long storyBibleId, Long relationId);

    int insertRelation(StoryBibleRelation relation);

    int updateRelation(StoryBibleRelation relation, Long expectedRevision);

    int softDeleteRelation(Long storyBibleId, Long relationId, Long expectedRevision, Long updatedBy);

    List<StoryBibleProgression> findProgressions(Long storyBibleId, List<Long> nodeIds);

    StoryBibleProgression findProgression(Long storyBibleId, Long progressionId);

    int insertProgression(StoryBibleProgression progression);

    int updateProgression(StoryBibleProgression progression, Long expectedRevision);

    int softDeleteProgression(Long storyBibleId, Long progressionId, Long expectedRevision, Long updatedBy);

    List<StoryBibleViewPreference> findViewPreferences(Long storyBibleId);

    int upsertViewPreference(StoryBibleViewPreference preference);

    int insertChangeset(StoryBibleChangeset changeset);

    int insertChangeItem(StoryBibleChangeItem item);

    List<StoryBibleChangeset> findRecentChangesets(Long storyBibleId, int limit);

    List<StoryBibleChangeset> findChangesetsPage(Long storyBibleId, Long beforeRevision, int limit);

    StoryBibleChangeset findChangeset(Long storyBibleId, Long changesetId);

    List<StoryBibleChangeset> findChangesetsBySourceRun(Long storyBibleId, Long sourceRunId);

    List<StoryBibleChangeset> findChangesetsByRevisionRange(Long storyBibleId, Long firstRevision, Long lastRevision);

    List<StoryBibleChangeset> findChangesetsForNode(Long storyBibleId, Long nodeId, int limit);

    List<StoryBibleChangeset> findChangesetsBefore(Long storyBibleId, Instant cutoff, int retainCount);

    List<StoryBible> findStoryBiblesWithChangesetsBefore(Instant cutoff, int retainCount);

    List<StoryBibleChangeItem> findChangeItemsByChangesetIds(List<Long> changesetIds);

    int archiveChangesets(Long storyBibleId, List<Long> changesetIds, Instant archivedAt);

    int markChangesetUndone(Long storyBibleId, Long changesetId, Long undoneBy, Long undoChangesetId);

    int markChangesetsUndone(Long storyBibleId, List<Long> changesetIds, Long undoneBy, Long undoChangesetId);

    StoryBibleNode findNodeIncludingDeleted(Long storyBibleId, Long nodeId);
    int restoreNode(StoryBibleNode node, Long expectedRevision);
    StoryBibleRelation findRelationIncludingDeleted(Long storyBibleId, Long relationId);
    int restoreRelation(StoryBibleRelation relation, Long expectedRevision);
    StoryBibleProgression findProgressionIncludingDeleted(Long storyBibleId, Long progressionId);
    int restoreProgression(StoryBibleProgression progression, Long expectedRevision);
    StoryBibleCategory findCategoryIncludingDeleted(Long storyBibleId, Long categoryId);
    int restoreCategory(StoryBibleCategory category);
    StoryBibleTag findTagIncludingDeleted(Long storyBibleId, Long tagId);
    int restoreTag(StoryBibleTag tag);
    StoryBibleNodeType findNodeTypeIncludingArchived(Long storyBibleId, Long typeId);
    int restoreNodeType(StoryBibleNodeType type);
}
