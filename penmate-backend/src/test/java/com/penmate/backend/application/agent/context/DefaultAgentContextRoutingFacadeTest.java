package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultAgentContextRoutingFacadeTest {

    @Mock
    private StoryBibleContextProvider storyBibleContextProvider;

    @Test
    void should_include_style_snapshot_when_preflight_enables_style_context() {
        DefaultAgentContextRoutingFacade facade = new DefaultAgentContextRoutingFacade(storyBibleContextProvider);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                true,
                false,
                false,
                "需要风格上下文",
                "{\"includeStyleContext\":true}"
        );

        AgentContextRoutingResult result = facade.route(new AgentContextRoutingRequest(
                1001L,
                2002L,
                3003L,
                "请续写雨夜回城后的场景",
                "{\"style\":\"noir\"}",
                decision
        ));

        assertThat(result.styleSnapshot()).isEqualTo("{\"style\":\"noir\"}");
        assertThat(result.storyBibleContext()).isEqualTo(new StoryBibleContextResult(false, "noop", ""));
    }

    @Test
    void should_not_call_story_bible_provider_when_preflight_disables_story_bible_context() {
        DefaultAgentContextRoutingFacade facade = new DefaultAgentContextRoutingFacade(storyBibleContextProvider);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "无需故事圣经",
                "{\"includeStoryBibleContext\":false}"
        );

        AgentContextRoutingResult result = facade.route(new AgentContextRoutingRequest(
                1001L,
                2002L,
                3003L,
                "请续写雨夜回城后的场景",
                "{\"style\":\"noir\"}",
                decision
        ));

        verify(storyBibleContextProvider, never()).loadContext(1001L, 2002L, 3003L, "请续写雨夜回城后的场景", decision);
        assertThat(result.styleSnapshot()).isNull();
        assertThat(result.storyBibleContext()).isEqualTo(new StoryBibleContextResult(false, "noop", ""));
    }

    @Test
    void noop_story_bible_context_provider_should_return_stable_result() {
        NoopStoryBibleContextProvider provider = new NoopStoryBibleContextProvider();
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE,
                "default",
                false,
                false,
                true,
                "预留 story bible",
                "{\"includeStoryBibleContext\":true}"
        );

        StoryBibleContextResult first = provider.loadContext(1001L, 2002L, 3003L, "角色设定是否一致", decision);
        StoryBibleContextResult second = provider.loadContext(1001L, 2002L, 3003L, "角色设定是否一致", decision);

        assertThat(first).isEqualTo(second);
        assertThat(first.enabled()).isFalse();
        assertThat(first.source()).isEqualTo("noop");
        assertThat(first.content()).isEmpty();
    }
}
