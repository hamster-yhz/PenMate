package com.penmate.backend.application.agent.orchestration.preflight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAgentPreflightCoordinatorTest {

    @Mock
    private AgentLlmGateway agentLlmGateway;

    @Mock
    private SystemPromptProvider systemPromptProvider;

    private DefaultAgentPreflightCoordinator coordinator;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        coordinator = new DefaultAgentPreflightCoordinator(
                agentLlmGateway,
                systemPromptProvider,
                new ObjectMapper()
        );
    }

    @Test
    void should_parse_llm_json_response_into_preflight_decision() {
        when(systemPromptProvider.loadBundle("preflight", "default")).thenReturn(new SystemPromptBundle(
                "preflight",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/preflight/default/00-base-role.md",
                        "你是 preflight 决策代理"
                )),
                "你是 preflight 决策代理"
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(null))).thenReturn(new AgentLlmTurnResponse(
                "stop",
                """
                        {
                          "behaviorType": "WRITE",
                          "executionPromptProfile": "default",
                          "includeStyleContext": true,
                          "includeRagContext": false,
                          "includeStoryBibleContext": false,
                          "reasoningSummary": "用户是在请求续写正文"
                        }
                        """,
                List.of(),
                "{\"id\":\"resp-1\"}"
        ));

        AgentPreflightDecision decision = coordinator.coordinate(new AgentPreflightRequest(
                1001L,
                2002L,
                3003L,
                "请续写主角在雨夜回城后的场景"
        ));

        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(systemPromptProvider).loadBundle("preflight", "default");
        verify(agentLlmGateway).generateTurn(requestCaptor.capture(), eq(null));
        assertThat(requestCaptor.getValue().messages()).hasSize(2);
        assertThat(requestCaptor.getValue().messages().get(0))
                .containsEntry("role", "system")
                .containsEntry("content", "你是 preflight 决策代理");
        assertThat(requestCaptor.getValue().messages().get(1))
                .containsEntry("role", "user");
        assertThat((String) requestCaptor.getValue().messages().get(1).get("content"))
                .contains("<preflight_request>")
                .contains("<project_id>1001</project_id>")
                .contains("<conversation_id>2002</conversation_id>")
                .contains("<chapter_id>3003</chapter_id>")
                .contains("<user_message>请续写主角在雨夜回城后的场景</user_message>")
                .doesNotContain("projectId=1001")
                .doesNotContain("conversationId=2002")
                .doesNotContain("chapterId=3003")
                .doesNotContain("userMessage=");
        assertThat(decision.behaviorType()).isEqualTo(AgentBehaviorType.WRITE);
        assertThat(decision.executionPromptProfile()).isEqualTo("default");
        assertThat(decision.includeStyleContext()).isTrue();
        assertThat(decision.includeRagContext()).isFalse();
        assertThat(decision.includeStoryBibleContext()).isFalse();
        assertThat(decision.reasoningSummary()).isEqualTo("用户是在请求续写正文");
        assertThat(decision.decisionTraceJson()).contains("\"behaviorType\":\"WRITE\"");
    }

    @Test
    void should_throw_when_required_field_is_missing_from_llm_json_response() {
        when(systemPromptProvider.loadBundle("preflight", "default")).thenReturn(new SystemPromptBundle(
                "preflight",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/preflight/default/00-base-role.md",
                        "你是 preflight 决策代理"
                )),
                "你是 preflight 决策代理"
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(null))).thenReturn(new AgentLlmTurnResponse(
                "stop",
                """
                        {
                          "behaviorType": "WRITE",
                          "includeStyleContext": true,
                          "includeRagContext": false,
                          "includeStoryBibleContext": false,
                          "reasoningSummary": "缺少 profile"
                        }
                        """,
                List.of(),
                null
        ));

        assertThatThrownBy(() -> coordinator.coordinate(new AgentPreflightRequest(
                1001L,
                2002L,
                null,
                "请续写"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executionPromptProfile");
    }

    @Test
    void should_throw_clear_error_when_behavior_type_is_invalid() {
        when(systemPromptProvider.loadBundle("preflight", "default")).thenReturn(new SystemPromptBundle(
                "preflight",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/preflight/default/00-base-role.md",
                        "你是 preflight 决策代理"
                )),
                "你是 preflight 决策代理"
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(null))).thenReturn(new AgentLlmTurnResponse(
                "stop",
                """
                        {
                          "behaviorType": "UNKNOWN_BEHAVIOR",
                          "executionPromptProfile": "default",
                          "includeStyleContext": true,
                          "includeRagContext": false,
                          "includeStoryBibleContext": false,
                          "reasoningSummary": "非法行为类型"
                        }
                        """,
                List.of(),
                null
        ));

        assertThatThrownBy(() -> coordinator.coordinate(new AgentPreflightRequest(
                1001L,
                2002L,
                3003L,
                "请分析请求"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("behaviorType is invalid");
    }

    @Test
    void should_throw_when_boolean_field_has_invalid_json_type() {
        when(systemPromptProvider.loadBundle("preflight", "default")).thenReturn(new SystemPromptBundle(
                "preflight",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/preflight/default/00-base-role.md",
                        "你是 preflight 决策代理"
                )),
                "你是 preflight 决策代理"
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(null))).thenReturn(new AgentLlmTurnResponse(
                "stop",
                """
                        {
                          "behaviorType": "WRITE",
                          "executionPromptProfile": "default",
                          "includeStyleContext": "yes",
                          "includeRagContext": false,
                          "includeStoryBibleContext": false,
                          "reasoningSummary": "布尔字段类型错误"
                        }
                        """,
                List.of(),
                null
        ));

        assertThatThrownBy(() -> coordinator.coordinate(new AgentPreflightRequest(
                1001L,
                2002L,
                3003L,
                "请续写"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("includeStyleContext must be boolean");
    }

    @Test
    void should_map_world_build_behavior_to_world_build_execution_profile() {
        when(systemPromptProvider.loadBundle("preflight", "default")).thenReturn(new SystemPromptBundle(
                "preflight",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/preflight/default/00-base-role.md",
                        "你是 preflight 决策代理"
                )),
                "你是 preflight 决策代理"
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(null))).thenReturn(new AgentLlmTurnResponse(
                "stop",
                """
                        {
                          "behaviorType": "WORLD_BUILD",
                          "executionPromptProfile": "default",
                          "includeStyleContext": false,
                          "includeRagContext": true,
                          "includeStoryBibleContext": false,
                          "reasoningSummary": "用户要补完设定"
                        }
                        """,
                List.of(),
                null
        ));

        AgentPreflightDecision decision = coordinator.coordinate(new AgentPreflightRequest(
                1001L,
                2002L,
                3003L,
                "补完帝国北境的地理、边防与补给体系"
        ));

        assertThat(decision.behaviorType()).isEqualTo(AgentBehaviorType.WORLD_BUILD);
        assertThat(decision.executionPromptProfile()).isEqualTo("world-build");
        assertThat(decision.includeRagContext()).isTrue();
    }

    @Test
    void should_enable_story_bible_flag_for_story_bible_query_candidate_behavior() {
        when(systemPromptProvider.loadBundle("preflight", "default")).thenReturn(new SystemPromptBundle(
                "preflight",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/preflight/default/00-base-role.md",
                        "你是 preflight 决策代理"
                )),
                "你是 preflight 决策代理"
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(null))).thenReturn(new AgentLlmTurnResponse(
                "stop",
                """
                        {
                          "behaviorType": "STORY_BIBLE_QUERY_CANDIDATE",
                          "executionPromptProfile": "default",
                          "includeStyleContext": false,
                          "includeRagContext": false,
                          "includeStoryBibleContext": false,
                          "reasoningSummary": "用户在查询长期设定一致性"
                        }
                        """,
                List.of(),
                null
        ));

        AgentPreflightDecision decision = coordinator.coordinate(new AgentPreflightRequest(
                1001L,
                2002L,
                3003L,
                "主角母亲在前三卷的故乡设定是否一致？"
        ));

        assertThat(decision.behaviorType()).isEqualTo(AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE);
        assertThat(decision.includeStoryBibleContext()).isTrue();
        assertThat(decision.executionPromptProfile()).isEqualTo("default");
    }
}
