package com.penmate.backend.interfaces.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryResult;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.interfaces.api.agent.dto.AgentRecoverySnapshotDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent 控制器回归测试。
 * <p>此处只冻结 session-agent 重构后仍应存在的新路由，
 * 并显式验证历史 conversation/message/generation 路由已不再暴露，避免回归时重新引入兼容运行时代码。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentConversationAppService agentConversationAppService;

    @Mock
    private AgentSessionRecoveryAppService agentSessionRecoveryAppService;

    @Mock
    private AgentTurnAppService agentTurnAppService;

    @InjectMocks
    private AgentController agentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void UT_AGENT_SESSION_LIST_SUCCESS() throws Exception {
        when(agentConversationAppService.listConversations(10001L))
                .thenReturn(List.of(conversation(90001L, "第三章夜雨追踪")));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].sessionId").value("90001"))
                .andExpect(jsonPath("$.data[0].conversationId").doesNotExist())
                .andExpect(jsonPath("$.data[0].title").value("第三章夜雨追踪"));

        verify(agentConversationAppService).listConversations(10001L);
    }

    @Test
    void UT_AGENT_SESSION_CREATE_SUCCESS() throws Exception {
        when(agentConversationAppService.createConversation(eq(10001L), any(), eq(null)))
                .thenReturn(conversation(90002L, "新会话"));

        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "1001",
                                "title", "新会话"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("90002"))
                .andExpect(jsonPath("$.data.conversationId").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("新会话"));

        verify(agentConversationAppService).createConversation(eq(10001L), any(), eq(null));
    }

    @Test
    void UT_AGENT_RECOVERY_GET_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-RECOVERY-GET";
        when(agentSessionRecoveryAppService.getRecovery(10001L, 90001L, traceId))
                .thenReturn(recoverySnapshot(90001L, "WAITING_APPROVAL"));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.activeTask.taskStatus").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));

        verify(agentSessionRecoveryAppService).getRecovery(10001L, 90001L, traceId);
    }

    @Test
    void UT_AGENT_RESUME_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-RESUME";
        when(agentSessionRecoveryAppService.resumeSession(eq(10001L), eq(90001L), eq(1001L), eq("WORKBENCH_ENTER"), eq(traceId)))
                .thenReturn(recoverySnapshot(90001L, "RUNNING"));

        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions/90001/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "1001",
                                "trigger", "WORKBENCH_ENTER"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.activeTask.taskStatus").value("RUNNING"));

        verify(agentSessionRecoveryAppService).resumeSession(10001L, 90001L, 1001L, "WORKBENCH_ENTER", traceId);
    }

    @Test
    void UT_AGENT_RESUME_PARAM_INVALID() throws Exception {
        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions/90001/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "1001",
                                "trigger", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_AGENT_TURN_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-TURN-CREATE";
        when(agentTurnAppService.createTurn(eq(10001L), eq(90001L), any(), eq(traceId)))
                .thenReturn(agentTask(90001L, "RUNNING", "WRITE", "继续扩写第三章"));

        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "1001",
                                "userMessage", "继续扩写第三章",
                                "taskRequest", Map.of(
                                        "taskType", "WRITE",
                                        "chapterId", "301",
                                        "selectedText", "夜雨中的追踪在巷口停住。"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.activeTask.taskStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data.taskType").value("WRITE"));

        verify(agentTurnAppService).createTurn(eq(10001L), eq(90001L), any(), eq(traceId));
    }

    @Test
    void UT_AGENT_TURN_PARAM_INVALID() throws Exception {
        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "1001",
                                "userMessage", "继续扩写第三章",
                                "taskRequest", Map.of(
                                        "taskType", "",
                                        "chapterId", "301"
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_AGENT_LEGACY_CONVERSATION_ROUTE_NOT_FOUND() throws Exception {
        mockMvc().perform(get("/api/v1/novels/10001/agent/conversations"))
                .andExpect(status().isNotFound());
    }

    @Test
    void UT_AGENT_LEGACY_MESSAGE_ROUTE_NOT_FOUND() throws Exception {
        mockMvc().perform(post("/api/v1/novels/10001/agent/conversations/7001/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void UT_AGENT_LEGACY_GENERATION_ROUTE_NOT_FOUND() throws Exception {
        mockMvc().perform(post("/api/v1/novels/10001/agent/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void UT_AGENT_LEGACY_GENERATION_APPLY_ROUTE_NOT_FOUND() throws Exception {
        mockMvc().perform(post("/api/v1/novels/10001/agent/generations/8001/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void UT_AGENT_LEGACY_STREAM_ROUTE_NOT_FOUND() throws Exception {
        mockMvc().perform(get("/api/v1/novels/10001/agent/generations/8002/stream"))
                .andExpect(status().isNotFound());
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

    private AgentSessionRecoveryResult recoverySnapshot(Long sessionId, String taskStatus) {
        return new AgentSessionRecoveryResult(
                new AgentSessionRecoveryResult.SessionView(
                        sessionId,
                        "第三章夜雨追踪",
                        "ACTIVE",
                        new AgentSessionRecoveryResult.BoundStyleView(81L, "冷峻悬疑"),
                        taskStatus
                ),
                new AgentSessionRecoveryResult.ActiveTaskView(70001L, taskStatus, 71001L),
                null,
                java.util.List.of(),
                null
        );
    }

    private AgentTurnResult agentTask(Long sessionId, String taskStatus, String taskType, String userMessage) {
        return new AgentTurnResult(
                new AgentTurnResult.SessionView(
                        sessionId,
                        "第三章夜雨追踪",
                        "ACTIVE",
                        new AgentTurnResult.BoundStyleView(81L, "冷峻悬疑"),
                        taskStatus
                ),
                new AgentTurnResult.ActiveTaskView(70001L, taskStatus, 71001L),
                taskType,
                userMessage
        );
    }
    @Test
    void UT_AGENT_REJECTS_LEGACY_PREFIX_IDS() throws Exception {
        String traceId = "UT-TRACE-AGENT-LEGACY-ID-REJECT";

        mockMvc().perform(get("/api/v1/novels/project-10001/agent/sessions/session-90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }
}
