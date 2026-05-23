package com.penmate.backend.interfaces.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.orchestration.AgentGenerationWorkflowDispatcher;
import com.penmate.backend.application.agent.query.AgentSessionRecoveryQueryService;
import com.penmate.backend.application.agent.query.AgentSessionTokenUsageQueryService;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionTokenUsageAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryResult;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.GenerationStreamService;
import com.penmate.backend.infrastructure.persistence.agent.AgentSessionMapper;
import com.penmate.backend.infrastructure.persistence.agent.AgentSessionRepositoryImpl;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
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
 * Task 10 端到端接口契约验收测试。
 * <p>此处冻结 Workbench runtime/recovery 所依赖的 turn/create/recovery HTTP 契约，
 * 用 A-F 矩阵把前后端对齐字段钉住：结果摘要、审批摘要、RAG 上下文、失败恢复与刷新去重均必须可从接口拿到。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentWorkflowEndToEndContractTest {

    @Mock
    private AgentConversationAppService agentConversationAppService;

    @Mock
    private AgentSessionRecoveryAppService agentSessionRecoveryAppService;

    @Mock
    private AgentTurnAppService agentTurnAppService;

    @Mock
    private AgentSessionRepository agentSessionRepository;

    @Mock
    private GenerationStreamService generationStreamService;

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
    void AT_AGENT_WORKFLOW_CASE_A_SHOULD_EXPOSE_DRAFT_RESULT_QUALITY_REPORT_AND_RUNTIME_SNAPSHOT_VIA_RECOVERY() throws Exception {
        String traceId = "AT-TRACE-CASE-A";
        when(agentSessionRecoveryAppService.getRecovery(10001L, 90001L, traceId))
                .thenReturn(recoverySnapshot(caseAWorkbenchContext(), caseAApproval(), List.of()));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.activeTask.turnId").value("50001"))
                .andExpect(jsonPath("$.data.activeTask.taskId").value("70001"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.lastRuntimeStatus").value("quality_review"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.recoveryCursor").value("tool_call:quality_review:call-quality-1"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.draftSummary.draftText").value("夜雨中的追踪在巷口停住。"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.qualityReportSummary.reviewSummary").value("存在剧情逻辑问题，需要修订。"));

        verify(agentSessionRecoveryAppService).getRecovery(10001L, 90001L, traceId);
    }

    @Test
    void AT_AGENT_WORKFLOW_CASE_B_SHOULD_EXPOSE_TODO_PLAN_CARD_SOURCE_AND_SESSION_SCOPED_ITEMS_VIA_RECOVERY() throws Exception {
        String traceId = "AT-TRACE-CASE-B";
        when(agentSessionRecoveryAppService.getRecovery(10001L, 90001L, traceId))
                .thenReturn(recoverySnapshot(caseBWorkbenchContext(), null, List.of()));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.lastRuntimeStatus").value("todo_review"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.planTitle").value("第三章修订待办"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.recommendedNextAction").value("apply_todo_plan"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.nextAction").doesNotExist())
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items[0].title").value("修复密令来源"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items[0].sessionId").value("90001"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items[1].title").value("补充侍从转述桥段"));
    }

    @Test
    void AT_AGENT_WORKFLOW_CASE_C_SHOULD_EXPOSE_STORY_BIBLE_PROPOSAL_SUMMARY_AND_WAITING_APPROVAL_SNAPSHOT() throws Exception {
        String traceId = "AT-TRACE-CASE-C";
        when(agentSessionRecoveryAppService.getRecovery(10001L, 90001L, traceId))
                .thenReturn(recoverySnapshot(caseCWorkbenchContext(), caseCApproval(), List.of(storyBibleApprovalMessage())));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingApproval.approvalId").value("88001"))
                .andExpect(jsonPath("$.data.pendingApproval.approvalType").value("STORY_BIBLE_UPDATE"))
                .andExpect(jsonPath("$.data.pendingApproval.toolCallId").value("call-story-1"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.lastRuntimeStatus").value("story_bible_review"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.storyBibleProposalSummary.proposalSummary").value("建议补充侍从知晓密令的设定"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.storyBibleProposalSummary.items[0].entryKey").value("maid.secret_order"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.storyBibleProposalSummary.entryKeys").doesNotExist())
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.storyBibleProposalSummary.nextAction").doesNotExist());
    }

    @Test
    void AT_AGENT_WORKFLOW_CASE_D_SHOULD_EXPOSE_RAG_REFS_AND_CONTEXT_SNAPSHOT_FOR_HYBRID_RETRIEVAL_RESUME() throws Exception {
        String traceId = "AT-TRACE-CASE-D";
        when(agentSessionRecoveryAppService.getRecovery(10001L, 90001L, traceId))
                .thenReturn(recoverySnapshot(caseDWorkbenchContext(), null, List.of()));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workbenchContext.chapterId").value("301"))
                .andExpect(jsonPath("$.data.workbenchContext.selectedText").value("沿着旧伏笔续写第 42 章"))
                .andExpect(jsonPath("$.data.workbenchContext.activePlugins[0]").value("hybrid.rag"))
                .andExpect(jsonPath("$.data.workbenchContext.modelConfigId").value("mcfg-9001"))
                .andExpect(jsonPath("$.data.workbenchContext.ragRefs[0]").value("chapter:42#伏笔-雨夜密令"))
                .andExpect(jsonPath("$.data.workbenchContext.outlineSnapshot.chapterTitle").value("第四十二章 雨夜旧令"));
    }

    @Test
    void AT_AGENT_WORKFLOW_CASE_E_SHOULD_EXPOSE_FAILURE_REASON_AND_NEXT_ACTION_FOR_RECOVERABLE_TOOL_FAILURE() throws Exception {
        String traceId = "AT-TRACE-CASE-E";
        when(agentSessionRecoveryAppService.getRecovery(10001L, 90001L, traceId))
                .thenReturn(recoverySnapshot(caseEWorkbenchContext(), null, List.of()));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeTask.taskStatus").value("failed"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.lastRuntimeStatus").value("failed"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.activeToolCallsSnapshot[0].status").value("failed"))
                .andExpect(jsonPath("$.data.workbenchContext.activeTaskRuntime.activeToolCallsSnapshot[0].errorMessage").value("质量审查超时"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary").doesNotExist());
    }

    @Test
    void AT_AGENT_WORKFLOW_CASE_F_SHOULD_KEEP_RECOVERY_SNAPSHOT_IDEMPOTENT_WITHOUT_DUPLICATED_TODOS_AFTER_REFRESH() throws Exception {
        String traceId = "AT-TRACE-CASE-F";
        AgentSessionRecoveryResult snapshot = recoverySnapshot(caseBWorkbenchContext(), null, List.of());
        when(agentSessionRecoveryAppService.resumeSession(10001L, 90001L, 1001L, "WORKBENCH_ENTER", traceId))
                .thenReturn(snapshot);
        when(agentSessionRecoveryAppService.getRecovery(10001L, 90001L, traceId))
                .thenReturn(snapshot);

        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions/90001/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "1001",
                                "trigger", "WORKBENCH_ENTER"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items.length()").value(2))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items[0].todoId").value("todo-1"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items[1].todoId").value("todo-2"));

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items.length()").value(2))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items[0].todoId").value("todo-1"))
                .andExpect(jsonPath("$.data.workbenchContext.resultSummary.todoSummary.items[1].todoId").value("todo-2"));
    }

    @Test
    void AT_AGENT_WORKFLOW_CREATE_TURN_CONTRACT_SHOULD_KEEP_NUMERIC_STRING_IDS_AND_DISPATCH_WORKFLOW() throws Exception {
        String traceId = "AT-TRACE-CREATE-TURN";
        when(agentTurnAppService.createTurn(eq(10001L), eq(90001L), any(), eq(traceId)))
                .thenReturn(agentTask(90001L, "PENDING", "WRITE", "续写本章并检查人设"));

        mockMvc().perform(post("/api/v1/novels/10001/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "1001",
                                "userMessage", "续写本章并检查人设",
                                "taskRequest", Map.of(
                                        "taskType", "WRITE",
                                        "chapterId", "301",
                                        "selectedText", "夜雨中的追踪在巷口停住。"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.activeTask.turnId").value("50001"))
                .andExpect(jsonPath("$.data.activeTask.taskId").value("70001"))
                .andExpect(jsonPath("$.data.taskType").value("WRITE"));

        verify(agentGenerationWorkflowDispatcher).dispatchInitialRun(10001L, 70001L, traceId);
    }

    @Test
    void AT_AGENT_WORKFLOW_CASE_D_SHOULD_EXPOSE_PERSISTED_STRUCTURED_SNAPSHOTS_FROM_REAL_RECOVERY_CHAIN() throws Exception {
        AgentSessionMapper mapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        BusinessIdGenerator businessIdGenerator = () -> 990001L;
        AgentSessionRepository repository = new AgentSessionRepositoryImpl(mapper, businessIdGenerator);
        PendingToolInvocationRepository pendingToolInvocationRepository = org.mockito.Mockito.mock(PendingToolInvocationRepository.class);
        AgentSessionRecoveryQueryService queryService = new AgentSessionRecoveryQueryService(repository, pendingToolInvocationRepository);
        AgentSessionRecoveryAppService recoveryAppService = new AgentSessionRecoveryAppService(queryService);
        AgentController controller = new AgentController(
                agentConversationAppService,
                recoveryAppService,
                new AgentSessionTokenUsageAppService(new AgentSessionTokenUsageQueryService(repository)),
                agentTurnAppService,
                agentGenerationWorkflowDispatcher,
                repository,
                generationStreamService
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(mapper.findSessionRow(101L, 90001L)).thenReturn(Map.of(
                "id", 1L,
                "sessionId", 90001L,
                "projectId", 101L,
                "ownerUserId", 201L,
                "title", "第四十二章 雨夜旧令",
                "sessionStatus", "ACTIVE",
                "boundStyleId", 81L,
                "activeContextVersion", 1,
                "lastTurnId", 50001L,
                "lastTaskId", 70001L
        ));
        when(mapper.findTaskRow(90001L, 70001L)).thenReturn(Map.of(
                "taskId", 70001L,
                "turnId", 50001L,
                "taskStatus", "RUNNING"
        ));
        Map<String, Object> contextRow = new LinkedHashMap<>();
        contextRow.put("contextId", 71001L);
        contextRow.put("taskId", 70001L);
        contextRow.put("chapterId", 301L);
        contextRow.put("selectedText", "沿着旧伏笔续写第 42 章");
        contextRow.put("outlineSnapshotJson", "{\"chapterTitle\":\"第四十二章 雨夜旧令\"}");
        contextRow.put("ragSnapshotJson", "{\"refs\":[\"chapter:42#伏笔-雨夜密令\"]}");
        contextRow.put("pluginBindingsJson", "[\"hybrid.rag\"]");
        contextRow.put("modelSnapshotJson", "{\"modelConfigId\":\"mcfg-9001\"}");
        contextRow.put("taskProfileJson", "{\"executionProfile\":\"default\",\"tools\":[\"story_bible_lookup\"],\"includeStoryBible\":true,\"includeRag\":true}");
        contextRow.put("promptPlanJson", "{\"finalProfile\":\"default\",\"assembledPromptPreview\":\"执行基座\",\"modules\":[{\"moduleKey\":\"execution:default\"}],\"skills\":[\"writer\"]}");
        contextRow.put("contextPackageJson", "{\"ragRefs\":[\"chapter:42#伏笔-雨夜密令\",\"version:42-v3#侍从转述\"],\"chapterScope\":\"chapter:301\",\"storyBibleEntries\":[\"maid.secret_order=侍从知道密令\"]}");
        contextRow.put("activeToolCallsSnapshot", "[{\"toolCode\":\"draft_generation\",\"status\":\"RUNNING\"}]");
        contextRow.put("lastRuntimeStatus", "DRAFT_GENERATION");
        contextRow.put("recoveryCursor", "tool_call:draft_generation:call-draft-1");
        contextRow.put("contextHash", "ctx-rag-42");
        when(mapper.findTaskContextRow(70001L)).thenReturn(contextRow);
        Map<String, Object> taskResultRow = new LinkedHashMap<>();
        taskResultRow.put("draftSummary", "{\"draftText\":\"密令在雨夜的屋檐下被再次提起。\"}");
        taskResultRow.put("qualityReportSummary", null);
        taskResultRow.put("todoSummary", null);
        taskResultRow.put("storyBibleProposalSummary", null);
        when(mapper.findTaskResultRow(70001L)).thenReturn(taskResultRow);
        when(mapper.listMessageRows(90001L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/novels/101/agent/sessions/90001/recovery")
                        .header("X-Trace-Id", "trace-real-recovery-case-d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workbenchContext.chapterId").value("301"))
                .andExpect(jsonPath("$.data.workbenchContext.selectedText").value("沿着旧伏笔续写第 42 章"))
                .andExpect(jsonPath("$.data.workbenchContext.activePlugins[0]").value("hybrid.rag"))
                .andExpect(jsonPath("$.data.workbenchContext.modelConfigId").value("mcfg-9001"))
                .andExpect(jsonPath("$.data.workbenchContext.ragRefs[0]").value("chapter:42#伏笔-雨夜密令"))
                .andExpect(jsonPath("$.data.workbenchContext.taskProfile.executionProfile").value("default"))
                .andExpect(jsonPath("$.data.workbenchContext.taskProfile.tools[0]").value("story_bible_lookup"))
                .andExpect(jsonPath("$.data.workbenchContext.promptPlan.finalProfile").value("default"))
                .andExpect(jsonPath("$.data.workbenchContext.promptPlan.modules[0].moduleKey").value("execution:default"))
                .andExpect(jsonPath("$.data.workbenchContext.contextPackage.chapterScope").value("chapter:301"))
                .andExpect(jsonPath("$.data.workbenchContext.contextPackage.storyBibleEntries[0]").value("maid.secret_order=侍从知道密令"));
    }

    @Test
    void AT_AGENT_WORKFLOW_STREAM_ROUTE_SHOULD_RESOLVE_TURN_TO_TASK_STREAM() throws Exception {
        AgentTaskContext taskContext = new AgentTaskContext();
        taskContext.setTurnId(50001L);
        taskContext.setTaskId(70001L);
        when(agentSessionRepository.findTaskByTurnId(10001L, 90001L, 50001L)).thenReturn(taskContext);
        when(generationStreamService.openStream(70001L)).thenReturn(new SseEmitter());

        mockMvc().perform(get("/api/v1/novels/10001/agent/sessions/90001/turns/50001/stream")
                        .header("Accept", "text/event-stream"))
                .andExpect(status().isOk());

        verify(agentSessionRepository).findTaskByTurnId(10001L, 90001L, 50001L);
        verify(generationStreamService).openStream(70001L);
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

    private AgentSessionRecoveryResult recoverySnapshot(Map<String, Object> workbenchContext,
                                                        Map<String, Object> pendingApproval,
                                                        List<Object> messages) {
        String taskStatus = String.valueOf(((Map<?, ?>) workbenchContext.get("activeTaskRuntime")).get("lastRuntimeStatus"));
        return new AgentSessionRecoveryResult(
                new AgentSessionRecoveryResult.SessionView(
                        90001L,
                        "第三章夜雨追踪",
                        "ACTIVE",
                        new AgentSessionRecoveryResult.BoundStyleView(81L, "冷峻悬疑"),
                        taskStatus
                ),
                new AgentSessionRecoveryResult.ActiveTaskView(50001L, 70001L, taskStatus, 71001L),
                pendingApproval,
                messages,
                workbenchContext
        );
    }

    private Map<String, Object> caseAWorkbenchContext() {
        Map<String, Object> context = baseWorkbenchContext("quality_review", "tool_call:quality_review:call-quality-1");
        Map<String, Object> resultSummary = new LinkedHashMap<>();
        resultSummary.put("draftSummary", Map.of(
                "draftText", "夜雨中的追踪在巷口停住。",
                "operation", "generate"
        ));
        resultSummary.put("qualityReportSummary", Map.of(
                "reviewSummary", "存在剧情逻辑问题，需要修订。",
                "needsRevision", true,
                "currentRevisionRound", 1,
                "maxRevisionRounds", 2
        ));
        resultSummary.put("todoSummary", null);
        resultSummary.put("storyBibleProposalSummary", null);
        context.put("resultSummary", resultSummary);
        return context;
    }

    private Map<String, Object> caseBWorkbenchContext() {
        Map<String, Object> context = baseWorkbenchContext("todo_review", "tool_call:todo_planner:call-todo-1");
        Map<String, Object> resultSummary = new LinkedHashMap<>();
        resultSummary.put("draftSummary", null);
        resultSummary.put("qualityReportSummary", Map.of(
                "reviewSummary", "存在剧情逻辑问题，需要修订。"
        ));
        resultSummary.put("todoSummary", Map.of(
                "planTitle", "第三章修订待办",
                "recommendedNextAction", "apply_todo_plan",
                "items", List.of(
                        Map.of(
                                "todoId", "todo-1",
                                "sessionId", "90001",
                                "title", "修复密令来源",
                                "status", "pending",
                                "priority", "HIGH"
                        ),
                        Map.of(
                                "todoId", "todo-2",
                                "sessionId", "90001",
                                "title", "补充侍从转述桥段",
                                "status", "pending",
                                "priority", "MEDIUM"
                        )
                )
        ));
        resultSummary.put("storyBibleProposalSummary", null);
        context.put("resultSummary", resultSummary);
        return context;
    }

    private Map<String, Object> caseCWorkbenchContext() {
        Map<String, Object> context = baseWorkbenchContext("story_bible_review", "approval:88001");
        Map<String, Object> resultSummary = new LinkedHashMap<>();
        resultSummary.put("draftSummary", null);
        resultSummary.put("qualityReportSummary", null);
        resultSummary.put("todoSummary", null);
        resultSummary.put("storyBibleProposalSummary", Map.of(
                "proposalSummary", "建议补充侍从知晓密令的设定",
                "items", List.of(Map.of(
                        "entryKey", "maid.secret_order",
                        "entryType", "CHARACTER_KNOWLEDGE",
                        "proposedContent", "侍从知晓密令并负责转述",
                        "canonicalStatus", "PROPOSED",
                        "riskLevel", 2,
                        "sourceText", "第二段侍从转述密令",
                        "sourceChapterId", 301,
                        "inferenceLevel", "DIRECT"
                ))
        ));
        context.put("resultSummary", resultSummary);
        return context;
    }

    private Map<String, Object> caseDWorkbenchContext() {
        Map<String, Object> context = baseWorkbenchContext("draft_generation", "tool_call:draft_generation:call-draft-1");
        context.put("outlineSnapshot", Map.of(
                "chapterTitle", "第四十二章 雨夜旧令"
        ));
        context.put("chapterId", "301");
        context.put("selectedText", "沿着旧伏笔续写第 42 章");
        context.put("activePlugins", List.of("hybrid.rag"));
        context.put("modelConfigId", "mcfg-9001");
        context.put("ragRefs", List.of("chapter:42#伏笔-雨夜密令", "version:42-v3#侍从转述"));
        Map<String, Object> resultSummary = new LinkedHashMap<>();
        resultSummary.put("draftSummary", Map.of(
                "draftText", "密令在雨夜的屋檐下被再次提起。"
        ));
        resultSummary.put("qualityReportSummary", null);
        resultSummary.put("todoSummary", null);
        resultSummary.put("storyBibleProposalSummary", null);
        context.put("resultSummary", resultSummary);
        return context;
    }

    private Map<String, Object> caseEWorkbenchContext() {
        Map<String, Object> context = baseWorkbenchContext("failed", "tool_call:quality_review:call-quality-timeout");
        context.put("activeTaskRuntime", Map.of(
                "lastRuntimeStatus", "failed",
                "recoveryCursor", "tool_call:quality_review:call-quality-timeout",
                "activeToolCallsSnapshot", List.of(
                        Map.of(
                                "toolCallId", "call-quality-timeout",
                                "toolCode", "quality_review",
                                "toolName", "质量审查",
                                "status", "failed",
                                "errorMessage", "质量审查超时"
                        )
                )
        ));
        Map<String, Object> resultSummary = new LinkedHashMap<>();
        resultSummary.put("draftSummary", null);
        resultSummary.put("qualityReportSummary", null);
        resultSummary.put("storyBibleProposalSummary", null);
        context.put("resultSummary", resultSummary);
        return context;
    }

    private Map<String, Object> baseWorkbenchContext(String lastRuntimeStatus, String recoveryCursor) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("outlineSnapshot", Map.of(
                "chapterTitle", "第三章 夜雨追踪"
        ));
        context.put("chapterId", "301");
        context.put("selectedText", "夜雨中的追踪在巷口停住。");
        context.put("activePlugins", List.of("outline.search", "story_bible_update"));
        context.put("modelConfigId", "mcfg-9001");
        context.put("ragRefs", List.of());
        context.put("activeTaskRuntime", Map.of(
                "lastRuntimeStatus", lastRuntimeStatus,
                "recoveryCursor", recoveryCursor,
                "activeToolCallsSnapshot", List.of(
                        Map.of(
                                "toolCallId", "call-quality-1",
                                "toolCode", "quality_review",
                                "toolName", "质量审查",
                                "status", "running",
                                "iteration", 1,
                                "argumentsPreview", "{\"chapterId\":\"301\"}",
                                "output", "{\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}",
                                "errorMessage", ""
                        )
                )
        ));
        return context;
    }

    private Map<String, Object> caseAApproval() {
        return Map.of(
                "approvalId", "0",
                "approvalType", "NONE",
                "toolCallId", "",
                "nextAction", "review_quality_report"
        );
    }

    private Map<String, Object> caseCApproval() {
        return Map.of(
                "approvalId", "88001",
                "approvalType", "STORY_BIBLE_UPDATE",
                "toolCallId", "call-story-1",
                "nextAction", "await_approval",
                "entryKeys", List.of("maid.secret_order")
        );
    }

    private Map<String, Object> storyBibleApprovalMessage() {
        return Map.of(
                "messageId", "1",
                "role", "assistant",
                "contentMd", "故事圣经更新待确认",
                "approvalId", "88001",
                "approvalType", "STORY_BIBLE_UPDATE"
        );
    }
}
