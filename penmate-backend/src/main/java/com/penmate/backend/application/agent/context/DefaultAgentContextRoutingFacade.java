package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.rag.HybridRagQuery;
import com.penmate.backend.application.rag.HybridRagResultView;
import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.application.rag.RagSearchScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 上下文路由门面。
 * <p>仅根据 preflight 决策决定是否透传风格快照、是否加载故事圣经，不在这里注入任何提示词语义。</p>
 */
@Slf4j
@Component
public class DefaultAgentContextRoutingFacade implements AgentContextRoutingFacade {

    private final StoryBibleContextProvider storyBibleContextProvider;
    private final DefaultContextBuilder contextBuilder;
    private final RagApplicationService ragApplicationService;

    public DefaultAgentContextRoutingFacade(StoryBibleContextProvider storyBibleContextProvider) {
        this(storyBibleContextProvider, new DefaultContextBuilder(), null);
    }

    @Autowired
    public DefaultAgentContextRoutingFacade(StoryBibleContextProvider storyBibleContextProvider,
                                            DefaultContextBuilder contextBuilder,
                                            RagApplicationService ragApplicationService) {
        this.storyBibleContextProvider = Objects.requireNonNull(storyBibleContextProvider, "storyBibleContextProvider");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder");
        this.ragApplicationService = ragApplicationService;
    }

    @Override
    public AgentContextRoutingResult route(AgentContextRoutingRequest request) {
        Objects.requireNonNull(request, "request");
        log.info("Agent 上下文路由开始: projectId={}, conversationId={}, chapterId={}, includeStyleContext={}, includeRagContext={}, includeStoryBibleContext={}",
                request.projectId(),
                request.conversationId(),
                request.chapterId(),
                request.decision().includeStyleContext(),
                request.decision().includeRagContext(),
                request.decision().includeStoryBibleContext());
        String styleSnapshot = request.decision().includeStyleContext() ? request.styleSnapshot() : null;
        List<StoryBibleContextEntryView> storyBibleEntries = request.decision().includeStoryBibleContext()
                ? storyBibleContextProvider.loadContext(
                request.projectId(),
                request.conversationId(),
                request.chapterId(),
                request.userMessage(),
                request.decision()
        )
                : List.of();
        List<HybridRagResultView> ragResults = loadRagResults(request, storyBibleEntries);
        ContextPackage contextPackage = contextBuilder.build(request, storyBibleEntries, ragResults);
        StoryBibleContextResult storyBibleContext = toLegacyStoryBibleContext(contextPackage);
        log.info("Agent 上下文路由完成: styleIncluded={}, ragRefs={}, storyBibleEnabled={}, storyBibleSource={}",
                styleSnapshot != null && !styleSnapshot.isBlank(),
                contextPackage.ragRefs().size(),
                storyBibleContext.enabled(),
                storyBibleContext.source());
        return new AgentContextRoutingResult(styleSnapshot, storyBibleContext, contextPackage);
    }

    private StoryBibleContextResult toLegacyStoryBibleContext(ContextPackage contextPackage) {
        if (contextPackage == null || contextPackage.storyBibleEntries().isEmpty()) {
            return StoryBibleContextResult.noop();
        }
        String source = contextPackage.sources().isEmpty() ? "noop" : contextPackage.sources().get(0);
        String content = String.join("\n", contextPackage.storyBibleEntries());
        return new StoryBibleContextResult(true, source, content);
    }

    private List<HybridRagResultView> loadRagResults(AgentContextRoutingRequest request,
                                                      List<StoryBibleContextEntryView> storyBibleEntries) {
        if (!request.decision().includeRagContext() || ragApplicationService == null) {
            return List.of();
        }
        return ragApplicationService.hybridSearch(new HybridRagQuery(
                request.projectId(),
                request.sessionId(),
                request.taskId(),
                request.chapterId(),
                resolveStoryBibleVersion(request, storyBibleEntries),
                request.taskProfile().skills(),
                request.decision().intentTags(),
                request.userMentionedEntities(),
                3,
                request.userMessage(),
                RagSearchScope.AGENT_CONTEXT
        ), "context-routing");
    }

    private Integer resolveStoryBibleVersion(AgentContextRoutingRequest request,
                                             List<StoryBibleContextEntryView> storyBibleEntries) {
        if (request.storyBibleVersion() != null) {
            return request.storyBibleVersion();
        }
        if (storyBibleEntries == null || storyBibleEntries.isEmpty()) {
            return null;
        }
        return storyBibleEntries.stream()
                .map(StoryBibleContextEntryView::versionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }
}
