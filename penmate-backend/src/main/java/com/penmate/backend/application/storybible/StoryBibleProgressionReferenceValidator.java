package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.novel.ManuscriptPositionResolver;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class StoryBibleProgressionReferenceValidator {
    private final ManuscriptPositionResolver manuscriptPositions;
    private final StoryBibleRepository storyBibles;

    public StoryBibleProgressionReferenceValidator(ManuscriptPositionResolver manuscriptPositions,
                                                   StoryBibleRepository storyBibles) {
        this.manuscriptPositions = manuscriptPositions;
        this.storyBibles = storyBibles;
    }

    public void validate(Long projectId, StoryBible storyBible, Long anchorChapterId,
                         Long endChapterId, Long storyEventNodeId) {
        Objects.requireNonNull(storyBible, "storyBible");
        var anchor = manuscriptPositions.resolve(projectId, anchorChapterId);
        if (!anchor.resolved()) {
            throw BusinessException.badRequest("Story Bible progression anchor chapter is unresolved");
        }
        if (endChapterId != null) {
            var end = manuscriptPositions.resolve(projectId, endChapterId);
            if (!end.resolved()) {
                throw BusinessException.badRequest("Story Bible progression end chapter is unresolved");
            }
            if (end.ordinal() < anchor.ordinal()) {
                throw BusinessException.badRequest("Story Bible progression end chapter precedes its anchor");
            }
        }
        if (storyEventNodeId == null) return;
        StoryBibleNode eventNode = storyBibles.findNode(storyBible.getStoryBibleId(), storyEventNodeId);
        if (eventNode == null) {
            throw BusinessException.notFound("Story Bible progression event node not found");
        }
        StoryBibleNodeType eventType = storyBibles.findNodeType(storyBible.getStoryBibleId(), eventNode.getTypeId());
        if (eventType == null || !"EVENT".equals(eventType.getTypeCode())) {
            throw BusinessException.badRequest("Story Bible progression event reference must target an EVENT node");
        }
    }
}
