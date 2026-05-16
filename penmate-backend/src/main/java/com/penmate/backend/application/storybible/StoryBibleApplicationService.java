package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class StoryBibleApplicationService {

    private final StoryBibleRepository storyBibleRepository;
    private final StoryBibleInitializationService storyBibleInitializationService;
    private final StoryBibleUpdateProposalService storyBibleUpdateProposalService;
    private final StoryBibleVersionSelector storyBibleVersionSelector;
    private final BusinessIdGenerator businessIdGenerator;

    public StoryBibleApplicationService(StoryBibleRepository storyBibleRepository,
                                        StoryBibleInitializationService storyBibleInitializationService,
                                        StoryBibleUpdateProposalService storyBibleUpdateProposalService,
                                        StoryBibleVersionSelector storyBibleVersionSelector) {
        this(storyBibleRepository, storyBibleInitializationService, storyBibleUpdateProposalService, storyBibleVersionSelector, null);
    }

    @Autowired
    public StoryBibleApplicationService(StoryBibleRepository storyBibleRepository,
                                        StoryBibleInitializationService storyBibleInitializationService,
                                        StoryBibleUpdateProposalService storyBibleUpdateProposalService,
                                        StoryBibleVersionSelector storyBibleVersionSelector,
                                        BusinessIdGenerator businessIdGenerator) {
        this.storyBibleRepository = Objects.requireNonNull(storyBibleRepository, "storyBibleRepository");
        this.storyBibleInitializationService = Objects.requireNonNull(storyBibleInitializationService, "storyBibleInitializationService");
        this.storyBibleUpdateProposalService = Objects.requireNonNull(storyBibleUpdateProposalService, "storyBibleUpdateProposalService");
        this.storyBibleVersionSelector = Objects.requireNonNull(storyBibleVersionSelector, "storyBibleVersionSelector");
        this.businessIdGenerator = businessIdGenerator;
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

    public StoryBibleEntry createEntry(Long projectId,
                                       StoryBibleEntry candidate,
                                       Long operatorId,
                                       String traceId) {
        StoryBible storyBible = storyBibleRepository.findByProjectId(projectId);
        StoryBibleEntry created = new StoryBibleEntry();
        created.setEntryId(candidate != null && candidate.getEntryId() != null ? candidate.getEntryId() : nextBusinessId());
        created.setProjectId(projectId);
        if (candidate != null) {
            created.setStoryBibleId(candidate.getStoryBibleId());
            created.setEntryType(candidate.getEntryType());
            created.setEntryKey(candidate.getEntryKey());
            created.setTitle(candidate.getTitle());
            created.setContent(candidate.getContent());
            created.setCanonicalStatus(candidate.getCanonicalStatus());
            created.setRiskLevel(candidate.getRiskLevel());
            created.setSourceRefs(candidate.getSourceRefs());
            created.setValidFromChapterId(candidate.getValidFromChapterId());
            created.setValidToChapterId(candidate.getValidToChapterId());
            created.setVersionNo(candidate.getVersionNo());
        }
        if (created.getStoryBibleId() == null && storyBible != null) {
            created.setStoryBibleId(storyBible.getStoryBibleId());
        }
        if (created.getVersionNo() == null && storyBible != null) {
            created.setVersionNo(storyBible.getActiveVersionNo());
        }
        if (created.getSourceRefs() == null) {
            created.setSourceRefs(List.of());
        }
        int affected = storyBibleRepository.insert(created);
        if (affected != 1) {
            throw BusinessException.of("story bible persistence failed");
        }
        return created;
    }

    public StoryBibleEntry updateEntry(Long projectId,
                                       Long entryId,
                                       StoryBibleEntry candidate,
                                       Long operatorId,
                                       String traceId) {
        StoryBibleEntry existing = storyBibleRepository.findByEntryId(projectId, entryId);
        if (existing == null) {
            throw BusinessException.notFound("Story bible entry not found");
        }
        StoryBibleEntry updated = new StoryBibleEntry();
        updated.setEntryId(entryId);
        updated.setProjectId(projectId);
        updated.setStoryBibleId(existing.getStoryBibleId());
        updated.setSourceRefs(existing.getSourceRefs() == null ? List.of() : existing.getSourceRefs());
        updated.setVersionNo(existing.getVersionNo());
        updated.setValidFromChapterId(existing.getValidFromChapterId());
        updated.setValidToChapterId(existing.getValidToChapterId());
        if (candidate != null) {
            updated.setEntryType(candidate.getEntryType());
            updated.setEntryKey(candidate.getEntryKey());
            updated.setTitle(candidate.getTitle());
            updated.setContent(candidate.getContent());
            updated.setCanonicalStatus(candidate.getCanonicalStatus());
            updated.setRiskLevel(candidate.getRiskLevel());
            if (candidate.getSourceRefs() != null) {
                updated.setSourceRefs(candidate.getSourceRefs());
            }
            if (candidate.getValidFromChapterId() != null) {
                updated.setValidFromChapterId(candidate.getValidFromChapterId());
            }
            updated.setValidToChapterId(candidate.getValidToChapterId());
            if (candidate.getVersionNo() != null) {
                updated.setVersionNo(candidate.getVersionNo());
            }
        }
        int affected = storyBibleRepository.update(updated);
        if (affected != 1) {
            throw BusinessException.notFound("Story bible entry not found");
        }
        return updated;
    }

    public void deleteEntry(Long projectId,
                            Long entryId,
                            Long operatorId,
                            String traceId) {
        int affected = storyBibleRepository.softDelete(projectId, entryId);
        if (affected != 1) {
            throw BusinessException.notFound("Story bible entry not found");
        }
    }

    private Long nextBusinessId() {
        if (businessIdGenerator != null) {
            return businessIdGenerator.nextId();
        }
        long fallback = Math.abs(System.nanoTime());
        return fallback == 0L ? 1L : fallback;
    }
}
