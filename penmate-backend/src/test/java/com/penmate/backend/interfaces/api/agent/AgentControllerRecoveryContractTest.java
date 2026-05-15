package com.penmate.backend.interfaces.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.orchestration.AgentGenerationWorkflowDispatcher;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryResult;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent session / turn / recovery 接口契约测试。
 * <p>本测试先冻结 session-agent 重构后的 REST 路径与关键字段命名，
 * 用于驱动控制器从 conversation/message/generation 三段式 API 重写为 recovery 模型。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerRecoveryContractTest {

    @Mock
    private AgentSessionRecoveryAppService agentSessionRecoveryAppService;

    @Mock
    private AgentTurnAppService agentTurnAppService;

    @Mock
    private AgentGenerationWorkflowDispatcher agentGenerationWorkflowDispatcher;

    @InjectMocks
    private AgentController agentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_return_recovery_snapshot() throws Exception {
        when(agentSessionRecoveryAppService.getRecovery(101L, 90001L, "trace-recovery-1"))
                .thenReturn(recoverySnapshot(90001L, "waiting_approval"));

        mockMvc().perform(get("/api/v1/novels/101/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", "trace-recovery-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.session.boundStyle.styleId").value("81"))
                .andExpect(jsonPath("$.data.activeTask.taskStatus").value("waiting_approval"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.lastRuntimeStatus").value("waiting_approval"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.recoveryCursor").value("approval:88001"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.activeToolCallsSnapshot[0].toolCode").value("quality_review"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.activeToolCallsSnapshot[0].status").value("waiting_approval"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.draftSummary.draftText").value("第三章初稿正文"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.qualityReportSummary.reviewSummary").value("存在剧情逻辑问题，需要修订。"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.planTitle").value("第三章修订待办"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.storyBibleProposalSummary.proposalSummary").value("建议补充侍从知晓密令的设定"))
                .andExpect(jsonPath("$.meta.traceId").value("trace-recovery-1"));

        verify(agentSessionRecoveryAppService).getRecovery(101L, 90001L, "trace-recovery-1");
    }

    @Test
    void should_resume_session_and_return_latest_recovery_snapshot() throws Exception {
        when(agentSessionRecoveryAppService.resumeSession(eq(101L), eq(90001L), eq(201L), eq("WORKBENCH_ENTER"), eq("trace-resume-1")))
                .thenReturn(recoverySnapshot(90001L, "running"));

        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/90001/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-resume-1")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "201",
                                "trigger", "WORKBENCH_ENTER"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.session.boundStyle.styleId").value("81"))
                .andExpect(jsonPath("$.data.activeTask.taskStatus").value("running"))
                .andExpect(jsonPath("$.meta.traceId").value("trace-resume-1"));

        verify(agentSessionRecoveryAppService).resumeSession(101L, 90001L, 201L, "WORKBENCH_ENTER", "trace-resume-1");
    }

    @Test
    void should_create_turn_and_return_task_view() throws Exception {
        when(agentTurnAppService.createTurn(eq(101L), eq(90001L), any(), eq("trace-turn-1")))
                .thenReturn(agentTask(90001L, "RUNNING", "WRITE", "继续扩写第三章夜雨追踪段落"));

        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-turn-1")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "201",
                                "userMessage", "继续扩写第三章夜雨追踪段落",
                                "taskRequest", Map.of(
                                        "taskType", "WRITE",
                                        "chapterId", "301",
                                        "selectedText", "夜雨中的追踪在巷口停住。"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.session.boundStyle.styleId").value("81"))
                .andExpect(jsonPath("$.data.activeTask.taskStatus").value("RUNNING"))
                .andExpect(jsonPath("$.meta.traceId").value("trace-turn-1"));

        verify(agentTurnAppService).createTurn(eq(101L), eq(90001L), any(), eq("trace-turn-1"));
        verify(agentGenerationWorkflowDispatcher).dispatchInitialRun(101L, 70001L, "trace-turn-1");
    }

    @Test
    void should_not_dispatch_generation_when_turn_result_has_no_task_id() throws Exception {
        when(agentTurnAppService.createTurn(eq(101L), eq(90001L), any(), eq("trace-turn-no-task")))
                .thenReturn(new AgentTurnResult(
                        new AgentTurnResult.SessionView(
                                90001L,
                                "第三章夜雨追踪",
                                "ACTIVE",
                                new AgentTurnResult.BoundStyleView(81L, "冷峻悬疑"),
                                "RUNNING"
                        ),
                        new AgentTurnResult.ActiveTaskView(50001L, null, "RUNNING", 71001L),
                        "WRITE",
                        "继续扩写"
                ));

        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-turn-no-task")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "201",
                                "userMessage", "继续扩写",
                                "taskRequest", Map.of(
                                        "taskType", "WRITE",
                                        "chapterId", "301"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.activeTask.turnId").value("50001"))
                .andExpect(jsonPath("$.data.activeTask.taskId").doesNotExist())
                .andExpect(jsonPath("$.meta.traceId").value("trace-turn-no-task"));

        verify(agentGenerationWorkflowDispatcher, never()).dispatchInitialRun(101L, null, "trace-turn-no-task");
    }

    @Test
    void should_reject_resume_request_when_trigger_is_blank() throws Exception {
        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/90001/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "201",
                                "trigger", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void should_reject_turn_request_when_task_type_is_blank() throws Exception {
        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "201",
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
    void should_keep_oversized_business_ids_as_string_in_recovery_and_turn_responses() throws Exception {
        Long oversizedSessionId = 9007199254740991L;
        Long oversizedStyleId = 9007199254740881L;
        Long oversizedTurnId = 9007199254740990L;
        Long oversizedTaskId = 9007199254740771L;
        Long oversizedRequestContextId = 9007199254740661L;

        when(agentSessionRecoveryAppService.getRecovery(101L, oversizedSessionId, "trace-oversized-recovery"))
                .thenReturn(new AgentSessionRecoveryResult(
                        new AgentSessionRecoveryResult.SessionView(
                                oversizedSessionId,
                                "超大 ID 会话",
                                "ACTIVE",
                                new AgentSessionRecoveryResult.BoundStyleView(oversizedStyleId, "冷峻悬疑"),
                                "RUNNING"
                        ),
                        new AgentSessionRecoveryResult.ActiveTaskView(oversizedTurnId, oversizedTaskId, "RUNNING", oversizedRequestContextId),
                        null,
                        java.util.List.of(),
                        null
                ));

        when(agentTurnAppService.createTurn(eq(101L), eq(oversizedSessionId), any(), eq("trace-oversized-turn")))
                .thenReturn(new AgentTurnResult(
                        new AgentTurnResult.SessionView(
                                oversizedSessionId,
                                "超大 ID 会话",
                                "ACTIVE",
                                new AgentTurnResult.BoundStyleView(oversizedStyleId, "冷峻悬疑"),
                                "RUNNING"
                        ),
                        new AgentTurnResult.ActiveTaskView(oversizedTurnId, oversizedTaskId, "RUNNING", oversizedRequestContextId),
                        "WRITE",
                        "继续扩写"
                ));

        mockMvc().perform(get("/api/v1/novels/101/agent/sessions/9007199254740991/recovery")
                        .header("X-Trace-Id", "trace-oversized-recovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("9007199254740991"))
                .andExpect(jsonPath("$.data.session.boundStyle.styleId").value("9007199254740881"))
                .andExpect(jsonPath("$.data.activeTask.turnId").value("9007199254740990"))
                .andExpect(jsonPath("$.data.activeTask.taskId").value("9007199254740771"))
                .andExpect(jsonPath("$.data.activeTask.requestContextId").value("9007199254740661"));

        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/9007199254740991/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-oversized-turn")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "201",
                                "userMessage", "继续扩写",
                                "taskRequest", Map.of(
                                        "taskType", "WRITE",
                                        "chapterId", "301"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("9007199254740991"))
                .andExpect(jsonPath("$.data.session.boundStyle.styleId").value("9007199254740881"))
                .andExpect(jsonPath("$.data.activeTask.turnId").value("9007199254740990"))
                .andExpect(jsonPath("$.data.activeTask.taskId").value("9007199254740771"))
                .andExpect(jsonPath("$.data.activeTask.requestContextId").value("9007199254740661"));

        verify(agentGenerationWorkflowDispatcher).dispatchInitialRun(101L, oversizedTaskId, "trace-oversized-turn");
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
                new AgentSessionRecoveryResult.ActiveTaskView(50001L, 70001L, taskStatus, 71001L),
                null,
                java.util.List.of(),
                Map.of(
                        "activeTaskRuntime", Map.of(
                                "lastRuntimeStatus", taskStatus,
                                "recoveryCursor", "approval:88001",
                                "activeToolCallsSnapshot", java.util.List.of(Map.of(
                                        "toolCallId", "tool-1",
                                        "toolCode", "quality_review",
                                        "status", "waiting_approval"
                                ))
                        ),
                        "resultSummary", Map.of(
                                "draftSummary", Map.of("draftText", "第三章初稿正文"),
                                "qualityReportSummary", Map.of("reviewSummary", "存在剧情逻辑问题，需要修订。"),
                                "todoSummary", Map.of("planTitle", "第三章修订待办"),
                                "storyBibleProposalSummary", Map.of("proposalSummary", "建议补充侍从知晓密令的设定")
                        )
                )
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
                new AgentTurnResult.ActiveTaskView(50001L, 70001L, taskStatus, 71001L),
                taskType,
                userMessage
        );
    }
}
