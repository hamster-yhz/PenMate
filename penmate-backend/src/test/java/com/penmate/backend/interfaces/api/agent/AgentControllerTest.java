package com.penmate.backend.interfaces.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.context.StoryBibleRoutingPreferenceResolver;
import com.penmate.backend.application.agent.context.StoryBibleRoutingMode;
import com.penmate.backend.application.agent.run.AgentRunRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionTokenUsageAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.infrastructure.realtime.AgentRunEventStreamService;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentConversationAppService agentConversationAppService;
    @Mock
    private AgentRunRecoveryAppService agentRunRecoveryAppService;
    @Mock
    private AgentSessionTokenUsageAppService agentSessionTokenUsageAppService;
    @Mock
    private AgentTurnAppService agentTurnAppService;
    @Mock
    private AgentRunEventStreamService agentRunEventStreamService;
    @Mock
    private StoryBibleRoutingPreferenceResolver routingPreferences;
    @InjectMocks
    private AgentController agentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void list_sessions_returns_session_ids_without_legacy_conversation_field() throws Exception {
        when(agentConversationAppService.listConversations(10001L))
                .thenReturn(List.of(conversation(90001L, "Session A")));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value("90001"))
                .andExpect(jsonPath("$.data[0].conversationId").doesNotExist())
                .andExpect(jsonPath("$.data[0].title").value("Session A"));

        verify(agentConversationAppService).listConversations(10001L);
    }

    @Test
    void create_session_uses_session_command() throws Exception {
        when(agentConversationAppService.createConversation(eq(10001L), any(CreateConversationCommand.class), eq(null)))
                .thenReturn(conversation(90002L, "New session"));

        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "1001",
                                "title", "New session"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("90002"))
                .andExpect(jsonPath("$.data.conversationId").doesNotExist());
    }

    @Test
    void create_turn_returns_active_run_contract() throws Exception {
        String traceId = "trace-agent-turn-create";
        when(agentTurnAppService.createTurn(eq(10001L), eq(90001L), any(), eq(traceId)))
                .thenReturn(AgentTurnResult.forRun(90001L, 50001L, 70001L, "running", "created", 1L));

        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "1001",
                                "userMessage", "Continue the chapter.",
                                "taskRequest", Map.of(
                                        "taskType", "WRITE",
                                        "chapterId", "301",
                                        "selectedText", "selected text"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRun.turnId").value("50001"))
                .andExpect(jsonPath("$.data.activeRun.runId").value("70001"))
                .andExpect(jsonPath("$.data.activeRun.runStatus").value("running"))
                .andExpect(jsonPath("$.data.activeTask").doesNotExist());
    }

    @Test
    void legacy_generation_routes_are_not_exposed() throws Exception {
        mockMvc().perform(post("/api/v1/novels/10001/agent/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc().perform(get("/api/v1/novels/10001/agent/generations/8002/stream"))
                .andExpect(status().isNotFound());

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/turns/50001/stream")
                        .header("Accept", "text/event-stream"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejects_legacy_prefix_ids() throws Exception {
        mockMvc().perform(get("/api/v1/novels/project-10001/agent/sessions/session-90001/recovery"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void gets_user_story_bible_routing_preference_with_string_model_id() throws Exception {
        when(routingPreferences.getUserDefault(1001L)).thenReturn(
                new StoryBibleRoutingPreferenceResolver.EffectivePreference(
                        StoryBibleRoutingMode.LLM_SELECTOR, 7001L, 9L, false));

        mockMvc().perform(get("/api/v1/novels/10001/agent/routing-preference")
                        .param("userId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LLM_SELECTOR"))
                .andExpect(jsonPath("$.data.routerModelConfigId").value("7001"))
                .andExpect(jsonPath("$.data.routerModelConfigRevision").value(9))
                .andExpect(jsonPath("$.data.inherited").value(false));
    }

    @Test
    void clears_session_override_and_returns_inherited_user_preference() throws Exception {
        when(routingPreferences.resolve(10001L, 90001L, 1001L)).thenReturn(
                new StoryBibleRoutingPreferenceResolver.EffectivePreference(
                        StoryBibleRoutingMode.RETRIEVAL_THEN_LLM, null, 0L, false));

        mockMvc().perform(put("/api/v1/novels/10001/agent/sessions/90001/routing-preference")
                        .param("userId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":null,\"routerModelConfigId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("RETRIEVAL_THEN_LLM"))
                .andExpect(jsonPath("$.data.routerModelConfigId").doesNotExist())
                .andExpect(jsonPath("$.data.inherited").value(true));

        verify(routingPreferences).saveSessionOverride(10001L, 90001L, 1001L, null, null);
    }

    private AgentConversation conversation(Long sessionId, String title) {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId(sessionId);
        conversation.setProjectId(10001L);
        conversation.setUserId(1001L);
        conversation.setTitle(title);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
