package com.penmate.backend.application.storybible;

import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class StoryBibleApplicationService {

    private final StoryBibleRepository storyBibleRepository;
    private final StoryBibleInitializationService storyBibleInitializationService;
    private final StoryBibleUpdateProposalService storyBibleUpdateProposalService;
    private final StoryBibleVersionSelector storyBibleVersionSelector;

    public StoryBibleApplicationService(StoryBibleRepository storyBibleRepository,
                                        StoryBibleInitializationService storyBibleInitializationService,
                                        StoryBibleUpdateProposalService storyBibleUpdateProposalService,
                                        StoryBibleVersionSelector storyBibleVersionSelector) {
        this.storyBibleRepository = Objects.requireNonNull(storyBibleRepository, "storyBibleRepository");
        this.storyBibleInitializationService = Objects.requireNonNull(storyBibleInitializationService, "storyBibleInitializationService");
        this.storyBibleUpdateProposalService = Objects.requireNonNull(storyBibleUpdateProposalService, "storyBibleUpdateProposalService");
        this.storyBibleVersionSelector = Objects.requireNonNull(storyBibleVersionSelector, "storyBibleVersionSelector");
    }

    public List<StoryBibleProposalItem> initializeFromIdea(Long projectId, String idea) {
        return storyBibleInitializationService.initializeFromIdea(projectId, idea);
    }

    public List<StoryBibleProposalItem> proposeUpdatesFromChapter(Long projectId, Long chapterId, String chapterText) {
        return storyBibleUpdateProposalService.proposeUpdatesFromChapter(projectId, chapterId, chapterText);
    }

    public List<StoryBibleEntry> listEntriesForChapter(Long projectId, Long chapterId) {
        List<StoryBibleEntry> activeEntries = storyBibleRepository.findActiveEntries(projectId, chapterId);
        return storyBibleVersionSelector.selectForChapter(activeEntries, chapterId);
    }
}
