package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 基于仓储的 Story Bible 上下文提供器。
 * <p>负责从 Story Bible 仓储读取当前章节有效条目，并转换为结构化上下文条目视图。</p>
 */
@Primary
@Component
public class RepositoryStoryBibleContextProvider implements StoryBibleContextProvider {

    private final StoryBibleRepository storyBibleRepository;

    public RepositoryStoryBibleContextProvider(StoryBibleRepository storyBibleRepository) {
        this.storyBibleRepository = Objects.requireNonNull(storyBibleRepository, "storyBibleRepository");
    }

    @Override
    public List<StoryBibleContextEntryView> loadContext(Long projectId,
                                                        Long conversationId,
                                                        Long chapterId,
                                                        String userMessage,
                                                        AgentPreflightDecision decision) {
        return storyBibleRepository.findActiveEntries(projectId, chapterId).stream()
                .map(this::toView)
                .toList();
    }

    private StoryBibleContextEntryView toView(StoryBibleEntry entry) {
        return new StoryBibleContextEntryView(
                "repository",
                entry == null ? null : entry.getEntryKey(),
                entry == null ? null : entry.getTitle(),
                entry == null ? null : entry.getContent(),
                entry == null ? null : entry.getEntryType(),
                entry == null ? null : entry.getCanonicalStatus(),
                entry == null ? null : entry.getRiskLevel(),
                entry == null ? null : entry.getVersionNo(),
                entry == null ? null : entry.getValidFromChapterId(),
                entry == null ? null : entry.getValidToChapterId()
        );
    }
}
