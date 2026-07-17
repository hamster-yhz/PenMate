package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContextEpochSnapshotFactory {
    private final StoryBibleRepository storyBibles;
    private final NovelGateway novels;

    public ContextEpochSnapshotFactory(StoryBibleRepository storyBibles, NovelGateway novels) {
        this.storyBibles = storyBibles;
        this.novels = novels;
    }

    public ContextEpochSnapshotCodec.Snapshot create(Long projectId, Long activeChapterId) {
        StoryBible root = storyBibles.findByProjectId(projectId);
        if (root == null) throw BusinessException.notFound("Story Bible not found");
        NovelProject project = novels.findProjectById(projectId);
        if (project == null) throw BusinessException.notFound("Novel project not found");
        Map<Long, StoryBibleNodeType> types = new HashMap<>();
        for (StoryBibleNodeType type : storyBibles.findNodeTypes(root.getStoryBibleId())) types.put(type.getTypeId(), type);
        List<StoryBibleNode> nodes = storyBibles.findNodes(
                root.getStoryBibleId(), null, StoryBibleCanonStatus.CANON.name(), null).stream()
                .sorted(Comparator.comparing((StoryBibleNode node) -> {
                    StoryBibleNodeType type = types.get(node.getTypeId());
                    return type == null || type.getSortOrder() == null ? Integer.MAX_VALUE : type.getSortOrder();
                }).thenComparing(StoryBibleNode::getTitle).thenComparing(StoryBibleNode::getNodeId))
                .toList();
        List<ContextEpochSnapshotCodec.CoreNode> core = nodes.stream()
                .filter(node -> node.getInclusionPolicy() == StoryBibleInclusionPolicy.ALWAYS_INCLUDE)
                .map(node -> new ContextEpochSnapshotCodec.CoreNode(node.getNodeId(), node.getTypeId(), node.getTitle(),
                        node.getSummary(), node.getBodyMarkdown(), node.getAttributesJson()))
                .toList();
        List<StoryBibleRouteRequest.CatalogEntry> catalog = nodes.stream().map(node -> {
            StoryBibleNodeType type = types.get(node.getTypeId());
            return new StoryBibleRouteRequest.CatalogEntry(node.getNodeId(), node.getTitle(),
                    type == null ? "UNKNOWN" : type.getTypeCode(), node.getSummary(),
                    node.getInclusionPolicy().name(), node.getCanonStatus().name());
        }).toList();
        NovelChapter activeChapter = activeChapterId == null ? null
                : novels.findChapterByIdAndProjectId(projectId, activeChapterId);
        long chapterRevision = activeChapter == null || activeChapter.getContentRevision() == null
                ? 0L : activeChapter.getContentRevision();
        return new ContextEpochSnapshotCodec.Snapshot(1, projectId, root.getStoryBibleId(), root.getContentRevision(),
                project.getStructureRevision() == null ? 0L : project.getStructureRevision(), activeChapterId,
                chapterRevision, core, catalog);
    }
}
