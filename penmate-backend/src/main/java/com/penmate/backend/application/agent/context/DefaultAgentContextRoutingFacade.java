package com.penmate.backend.application.agent.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 上下文路由门面。
 * <p>仅根据 preflight 决策决定是否透传风格快照、是否加载故事圣经，不在这里注入任何提示词语义。</p>
 */
@Slf4j
@Component
public class DefaultAgentContextRoutingFacade implements AgentContextRoutingFacade {

    private final StoryBibleContextProvider storyBibleContextProvider;

    public DefaultAgentContextRoutingFacade(StoryBibleContextProvider storyBibleContextProvider) {
        this.storyBibleContextProvider = Objects.requireNonNull(storyBibleContextProvider, "storyBibleContextProvider");
    }

    @Override
    public AgentContextRoutingResult route(AgentContextRoutingRequest request) {
        Objects.requireNonNull(request, "request");
        log.info("Agent 上下文路由开始: projectId={}, conversationId={}, chapterId={}, includeStyleContext={}, includeStoryBibleContext={}",
                request.projectId(),
                request.conversationId(),
                request.chapterId(),
                request.decision().includeStyleContext(),
                request.decision().includeStoryBibleContext());
        String styleSnapshot = request.decision().includeStyleContext() ? request.styleSnapshot() : null;
        StoryBibleContextResult storyBibleContext = request.decision().includeStoryBibleContext()
                ? storyBibleContextProvider.loadContext(
                        request.projectId(),
                        request.conversationId(),
                        request.chapterId(),
                        request.userMessage(),
                        request.decision()
                )
                : StoryBibleContextResult.noop();
        log.info("Agent 上下文路由完成: styleIncluded={}, storyBibleEnabled={}, storyBibleSource={}",
                styleSnapshot != null && !styleSnapshot.isBlank(),
                storyBibleContext.enabled(),
                storyBibleContext.source());
        return new AgentContextRoutingResult(styleSnapshot, storyBibleContext);
    }
}
