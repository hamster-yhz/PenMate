package com.penmate.backend.application.agent.orchestration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.context.AgentContextRoutingFacade;
import com.penmate.backend.application.agent.runtime.RuntimeStatusView;
import com.penmate.backend.application.agent.runtime.StoryBibleApprovalView;
import com.penmate.backend.application.agent.runtime.TaskRuntimeStatusPublisher;
import com.penmate.backend.application.agent.runtime.ToolCallStatusView;
import com.penmate.backend.application.agent.context.AgentContextRoutingRequest;
import com.penmate.backend.application.agent.context.AgentContextRoutingResult;
import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.context.StoryBibleContextResult;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightCoordinator;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightRequest;
import com.penmate.backend.application.agent.orchestration.profile.TaskIntentTag;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfileMapper;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.storybible.StoryBibleProposalItem;
import com.penmate.backend.application.storybible.StoryBibleUpdateProposalService;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.application.todo.TodoPlanItemView;
import com.penmate.backend.application.todo.TodoPlanView;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.todo.model.SessionTodo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 生成主工作流。
 * <p>该工作流负责把一次生成任务的完整链路串起来：任务状态推进、模型执行配置解析、prompt 装配、tool loop、结果发布与失败封口。</p>
 * <p>它是跨应用服务、领域仓储、外部网关的长流程协调者，因此更偏 orchestration，而不是单一 CRUD 应用服务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGenerationWorkflow {

    private static final int ERROR_MSG_MAX_LENGTH = 500;
    private static final Pattern CJK_ENTITY_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF]{2,4}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentRepository agentRepository;
    private final com.penmate.backend.application.agent.AgentTaskStateMachine taskStateMachine;
    private final AgentToolLoopRunner agentToolLoopRunner;
    private final AgentModelRoutingService agentModelRoutingService;
    private final AgentPromptAssembler agentPromptAssembler;
    private final PromptComposer promptComposer;
    private final AgentPreflightCoordinator agentPreflightCoordinator;
    private final AgentContextRoutingFacade agentContextRoutingFacade;
    private final AgentResultPublisher agentResultPublisher;
    private final TaskRuntimeStatusPublisher taskRuntimeStatusPublisher;
    private final AgentTaskRuntimeUpdater agentTaskRuntimeUpdater;
    private final AgentTaskResultRecorder agentTaskResultRecorder;
    private final TodoCrudApplicationService todoCrudApplicationService;
    private final StoryBibleUpdateProposalService storyBibleUpdateProposalService;
    private final ToolCallExecutionService toolCallExecutionService;
    private final ApprovalApplicationService approvalApplicationService;
    private final PendingToolInvocationRepository pendingToolInvocationRepository;
    private final SessionStyleBindingAppService sessionStyleBindingAppService;
    private final IamGateway iamGateway;

    public void run(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId);
    }

    public void runAfterApproval(Long projectId, Long taskId, String traceId) {
        runInternal(projectId, taskId, traceId);
    }

    private void runInternal(Long projectId, Long taskId, String traceId) {
        log.info("Agent 生成工作流开始: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
        AgentGenerationTask task = agentRepository.findGenerationTask(projectId, taskId);
        if (task == null) {
            log.warn("编排任务不存在: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
            return;
        }
        if (task.getTaskId() == null) {
            log.error("编排任务缺少 taskId，终止执行: projectId={}, requestedTaskId={}, physicalId={}, traceId={}",
                    projectId,
                    taskId,
                    task.getId(),
                    traceId);
            return;
        }
 
        boolean resumedFromWaitingApproval = AgentTaskStatus.WAITING_APPROVAL.value().equalsIgnoreCase(task.getStatus());
        AgentTaskContext taskContext = buildTaskContext(projectId, task);

        try {
            transitionStatus(projectId, task, AgentTaskStatus.RUNNING, null);
            syncRuntimeSnapshot(projectId, task, taskContext, null, null, null, "planning", "phase:planning");
            taskRuntimeStatusPublisher.publishStarted(projectId,
                    buildRuntimeStatusView(task, taskContext, "planning", "正在分析请求", true, "run_preflight", null));

            String promptSnapshot = requirePromptSnapshot(task, traceId);
            
            // 1. 预检分析阶段，协调模型确定执行策略和画像
            AgentPreflightDecision preflightDecision = executePreflightPhase(projectId, task, taskContext, promptSnapshot, traceId);
            TaskProfile taskProfile = TaskProfileMapper.from(preflightDecision);
            
            // 2. 规划并组装 Prompt 阶段
            PromptPlan promptPlan = executePromptPlanPhase(projectId, task, taskContext, taskProfile, promptSnapshot);
            
            // 3. 收集执行所需的各种上下文资源（如记忆、规则、文档等）阶段
            ContextPackage contextPackage = executeContextRoutingPhase(projectId, taskId, task, taskContext, taskProfile, preflightDecision, promptPlan, promptSnapshot);

            // 4. 执行 Tool Loop 生成主逻辑阶段
            AgentToolLoopIterationResult loopResult = executeToolLoopPhase(projectId, taskId, task, taskContext, taskProfile, promptPlan, contextPackage, promptSnapshot, traceId);

            if (loopResult.waitingApproval()) {
                handleWaitingApproval(projectId, taskId, task, taskContext, taskProfile, promptPlan, contextPackage,
                        loopResult.approvalId(), loopResult, loopResult.toolContext(), traceId);
                return;
            }
            
            String generatedText = loopResult.finalAssistantText();
            // 5. 检查并应用改写逻辑
            RevisionDecision revisionDecision = applyControlledRevisionIfNeeded(
                    projectId,
                    task,
                    taskProfile,
                    generatedText,
                    loopResult.toolContext(),
                    traceId
            );
            String finalText = revisionDecision.generatedText();
            String effectiveToolTrace = revisionDecision.toolTraceJson();
            
            // 6. 如果有 TODO 计划则落库
            persistTodoPlanIfPresent(projectId, task, taskProfile, effectiveToolTrace, traceId);
            
            // 7. 处理故事设定集的提案和审批
            StoryBibleDecision storyBibleDecision = handleStoryBibleProposals(
                    projectId,
                    task,
                    taskContext,
                    taskProfile,
                    finalText,
                    effectiveToolTrace,
                    traceId,
                    resumedFromWaitingApproval
            );
            
            if (storyBibleDecision.waitingApproval()) {
                agentTaskResultRecorder.recordAssistantResult(task, finalText, storyBibleDecision.toolTraceJson());
                agentRepository.updateGenerationTaskActiveApproval(projectId, task.getTaskId(), storyBibleDecision.approvalId());
                taskContext.setActiveApprovalId(storyBibleDecision.approvalId());
                AgentToolLoopIterationResult approvalSnapshot = AgentToolLoopIterationResult.waitingApproval(
                        storyBibleDecision.approvalId(), loopResult.toolCallCount(), storyBibleDecision.toolTraceJson());
                handleWaitingApproval(projectId, taskId, task, taskContext, taskProfile, promptPlan, contextPackage,
                        storyBibleDecision.approvalId(), approvalSnapshot, storyBibleDecision.toolTraceJson(), traceId);
                return;
            }

            // 8. 记录结果、更新状态并收尾
            finalizeTask(projectId, taskId, task, taskContext, taskProfile, promptPlan, contextPackage, finalText, storyBibleDecision.toolTraceJson(), traceId);
        } catch (Exception ex) {
            handleTaskFailure(projectId, taskId, task, taskContext, traceId, ex);
        }
    }

    /**
     * 执行预检分析阶段，协调模型确定执行策略和画像。
     */
    private AgentPreflightDecision executePreflightPhase(Long projectId, AgentGenerationTask task, AgentTaskContext taskContext, String promptSnapshot, String traceId) {
        AgentLlmExecutionConfig preflightExecutionConfig = resolvePreflightExecutionConfig(task, traceId);
        AgentPreflightDecision preflightDecision = agentPreflightCoordinator.coordinate(new AgentPreflightRequest(
                projectId,
                task.getConversationId(),
                taskContext.getChapterId(),
                promptSnapshot,
                preflightExecutionConfig
        ));
        TaskProfile taskProfile = TaskProfileMapper.from(preflightDecision);
        syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, null, null, "planning", "phase:planning");
        taskRuntimeStatusPublisher.publishStatus(projectId,
                buildRuntimeStatusView(task, taskContext, "planning", "正在分析请求", true, "compose_prompt", null));
        publishDerivedReviewPhases(projectId, task, taskContext, taskProfile);
        return preflightDecision;
    }

    /**
     * 规划并组装 Prompt。
     */
    private PromptPlan executePromptPlanPhase(Long projectId, AgentGenerationTask task, AgentTaskContext taskContext, TaskProfile taskProfile, String promptSnapshot) {
        PromptPlan promptPlan = promptComposer.compose(taskProfile, emptyContextPackage(), promptSnapshot);
        syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, promptPlan, null, "planning", "phase:planning");
        taskRuntimeStatusPublisher.publishStatus(projectId,
                buildRuntimeStatusView(task, taskContext, "planning", "正在规划章节", true, "route_context", null));
        return promptPlan;
    }

    /**
     * 收集执行所需的各种上下文资源（如记忆、规则、文档等）。
     */
    private ContextPackage executeContextRoutingPhase(Long projectId, Long taskId, AgentGenerationTask task, AgentTaskContext taskContext, TaskProfile taskProfile, AgentPreflightDecision preflightDecision, PromptPlan promptPlan, String promptSnapshot) {
        AgentContextRoutingResult routingResult = agentContextRoutingFacade.route(new AgentContextRoutingRequest(
                projectId,
                task.getConversationId(),
                task.getConversationId(),
                task.getTaskId(),
                taskContext.getChapterId(),
                null,
                extractUserMentionedEntities(promptSnapshot, taskContext.getSelectedText()),
                promptSnapshot,
                taskContext.getStyleSnapshotJson(),
                preflightDecision,
                taskProfile
        ));
        log.info("Agent 路由决策已生效: taskId={}, executionProfile={}, includeStyleContext={}, ragRouteEnabled={}, includeStoryBibleContext={}, storyBibleEnabled={}",
                taskId,
                preflightDecision.executionPromptProfile(),
                preflightDecision.includeStyleContext(),
                preflightDecision.includeRagContext(),
                preflightDecision.includeStoryBibleContext(),
                routingResult.storyBibleContext().enabled());
        taskContext.setStyleSnapshotJson(routingResult.styleSnapshot());
        ContextPackage contextPackage = buildContextPackage(taskContext, routingResult, preflightDecision);
        persistStructuredSnapshots(projectId, taskId, taskContext, taskProfile, promptPlan, contextPackage);
        syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, promptPlan, contextPackage, "planning", "phase:planning");
        taskRuntimeStatusPublisher.publishStatus(projectId,
                buildRuntimeStatusView(task, taskContext, "planning", "正在整理上下文", true, "start_execution", null));
        return contextPackage;
    }

    /**
     * 执行 Tool Loop 生成主逻辑。
     */
    private AgentToolLoopIterationResult executeToolLoopPhase(Long projectId, Long taskId, AgentGenerationTask task, AgentTaskContext taskContext, TaskProfile taskProfile, PromptPlan promptPlan, ContextPackage contextPackage, String promptSnapshot, String traceId) {
        AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(task.getUserId(), task.getModelConfigId(), traceId);
        syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, promptPlan, contextPackage, "executing", "phase:executing");
        taskRuntimeStatusPublisher.publishStatus(projectId,
                buildRuntimeStatusView(task, taskContext, "executing", "正在生成正文", true, "run_tool_loop", null));
        long llmStartAt = System.currentTimeMillis();
        AgentToolLoopIterationResult loopResult = agentToolLoopRunner.execute(
                projectId,
                taskId,
                task.getConversationId(),
                0L,
                traceId,
                agentPromptAssembler.buildExecutionMessages(
                        promptPlan,
                        contextPackage,
                        promptSnapshot
                ),
                executionConfig
        );
        if (!loopResult.waitingApproval()) {
            long llmCostMs = System.currentTimeMillis() - llmStartAt;
            log.info("agent.llm.generate.finished: projectId={}, taskId={}, traceId={}, costMs={}, outputLength={}",
                    projectId,
                    taskId,
                    traceId,
                    llmCostMs,
                    safeLength(loopResult.finalAssistantText()));
        }
        return loopResult;
    }

    /**
     * 处理任务待审批状态。
     */
    private void handleWaitingApproval(Long projectId, Long taskId, AgentGenerationTask task, AgentTaskContext taskContext, TaskProfile taskProfile, PromptPlan promptPlan, ContextPackage contextPackage, Long approvalId, AgentToolLoopIterationResult snapshotResult, String toolTraceJson, String traceId) {
        log.info("Agent 工作流进入待审批: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
        transitionStatus(projectId, task, AgentTaskStatus.WAITING_APPROVAL, null);
        ensureWaitingApprovalToolSnapshot(taskContext, snapshotResult);
        syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, promptPlan, contextPackage, "waiting_approval", "approval:" + approvalId);
        taskRuntimeStatusPublisher.publishWaitingApproval(projectId,
                buildRuntimeStatusView(task, taskContext, "waiting_approval", "等待审批", true, "await_approval", toolTraceJson));
    }

    /**
     * 完成任务记录并发布状态。
     */
    private void finalizeTask(Long projectId, Long taskId, AgentGenerationTask task, AgentTaskContext taskContext, TaskProfile taskProfile, PromptPlan promptPlan, ContextPackage contextPackage, String finalText, String toolTraceJson, String traceId) {
        agentTaskResultRecorder.recordAssistantResult(task, finalText, toolTraceJson);
        agentTaskRuntimeUpdater.updateGenerationRuntime(
                projectId,
                taskId,
                task.getPromptSnapshot(),
                finalText,
                traceId,
                taskProfile,
                promptPlan,
                contextPackage
        );
        agentResultPublisher.publishGenerationTokens(projectId, taskId, finalText, traceId);
        transitionStatus(projectId, task, AgentTaskStatus.DONE, null);
        syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, promptPlan, contextPackage, "done", "done");
        taskRuntimeStatusPublisher.publishDone(projectId,
                buildRuntimeStatusView(task, taskContext, "done", "已完成", false, "show_result", toolTraceJson));
        log.info("Agent 生成工作流完成: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
    }

    /**
     * 处理任务失败状态。
     */
    private void handleTaskFailure(Long projectId, Long taskId, AgentGenerationTask task, AgentTaskContext taskContext, String traceId, Exception ex) {
        log.error("编排执行失败: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId, ex);
        transitionToFailed(projectId, task, ex);
        syncRuntimeSnapshot(projectId, task, taskContext, null, null, null, "failed", "failed");
        taskRuntimeStatusPublisher.publishFailed(projectId,
                buildRuntimeStatusView(task, taskContext, "failed", safeErrorMessage(ex), true, "retry_task", null));
    }

    private AgentTaskContext buildTaskContext(Long projectId, AgentGenerationTask task) {
        AgentTaskContext persistedContext = task == null || task.getTaskId() == null
                ? null
                : agentRepository.findTaskContext(task.getTaskId());
        if (persistedContext != null) {
            if (persistedContext.getTaskStatus() == null && task != null) {
                persistedContext.setTaskStatus(task.getStatus());
            }
            return persistedContext;
        }
        AgentTaskContext taskContext = AgentTaskContext.recoveryOf(task.getTaskId(), task.getStatus(), null);
        if (sessionStyleBindingAppService != null) {
            taskContext.setStyleSnapshotJson(sessionStyleBindingAppService.getBoundStyleSnapshotJson(projectId, task.getConversationId()));
        }
        return taskContext;
    }

    private ContextPackage buildContextPackage(AgentTaskContext taskContext,
                                               AgentContextRoutingResult routingResult,
                                               AgentPreflightDecision preflightDecision) {
        Objects.requireNonNull(taskContext, "taskContext");
        Objects.requireNonNull(routingResult, "routingResult");
        Objects.requireNonNull(preflightDecision, "preflightDecision");
        return Objects.requireNonNull(routingResult.contextPackage(), "contextPackage");
    }

    private ContextPackage emptyContextPackage() {
        return new ContextPackage(List.of(), List.of(), List.of(), List.of(), List.of(), "", "");
    }

    private void persistTodoPlanIfPresent(Long projectId,
                                          AgentGenerationTask task,
                                          TaskProfile taskProfile,
                                          String toolTraceJson,
                                          String traceId) {
        if (task == null || todoCrudApplicationService == null || taskProfile == null || !taskProfile.tools().contains("todo_planner")) {
            return;
        }
        TodoPlanView todoPlanView = extractTodoPlan(toolTraceJson);
        if (todoPlanView == null) {
            return;
        }
        List<SessionTodo> todosToCreate = toSessionTodos(todoPlanView);
        if (todosToCreate.isEmpty()) {
            return;
        }
        todoCrudApplicationService.batchCreateTodos(
                projectId,
                task.getConversationId(),
                task.getTaskId(),
                todosToCreate,
                task.getUserId(),
                traceId
        );
    }

    private TodoPlanView extractTodoPlan(String toolTraceJson) {
        String normalized = normalizeToolTraceJson(toolTraceJson);
        if (normalized == null) {
            return null;
        }
        String[] fragments = normalized.split("\\r?\\n+");
        for (String fragment : fragments) {
            TodoPlanView candidate = parseTodoPlan(fragment);
            if (candidate != null) {
                return candidate;
            }
        }
        return parseTodoPlan(normalized);
    }

    private TodoPlanView parseTodoPlan(String rawJson) {
        String normalized = normalizeToolTraceJson(rawJson);
        if (normalized == null) {
            return null;
        }
        try {
            Map<String, Object> json = OBJECT_MAPPER.readValue(normalized, new TypeReference<Map<String, Object>>() {
            });
            if (!json.containsKey("planTitle") || !json.containsKey("planSummary") || !json.containsKey("recommendedNextAction") || !json.containsKey("items")) {
                return null;
            }
            List<TodoPlanItemView> items = new java.util.ArrayList<>();
            Object itemValue = json.get("items");
            if (itemValue instanceof List<?> itemList) {
                for (Object entry : itemList) {
                    if (!(entry instanceof Map<?, ?> itemMap)) {
                        continue;
                    }
                    items.add(new TodoPlanItemView(
                            stringValue(itemMap.get("title")),
                            stringValue(itemMap.get("description")),
                            stringValue(itemMap.get("priority")),
                            stringValue(itemMap.get("sourceType")),
                            stringValue(itemMap.get("recommendedStatus")),
                            Boolean.TRUE.equals(itemMap.get("suggestedAutoCreate")),
                            stringValue(itemMap.get("rationale")),
                            stringList(itemMap.get("acceptanceCriteria")),
                            stringList(itemMap.get("dependsOn"))
                    ));
                }
            }
            return new TodoPlanView(
                    stringValue(json.get("planTitle")),
                    stringValue(json.get("planSummary")),
                    stringValue(json.get("recommendedNextAction")),
                    items
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private RevisionDecision applyControlledRevisionIfNeeded(Long projectId,
                                                             AgentGenerationTask task,
                                                             TaskProfile taskProfile,
                                                             String generatedText,
                                                             String toolTraceJson,
                                                             String traceId) {
        String preservedToolTrace = preserveToolTraceForRecorder(toolTraceJson);
        Map<String, Object> qualityReport = extractQualityReport(toolTraceJson);
        if (toolCallExecutionService == null
                || task == null
                || taskProfile == null
                || qualityReport == null
                || !Boolean.TRUE.equals(qualityReport.get("needsRevision"))
                || !Boolean.TRUE.equals(qualityReport.get("revisionAllowed"))
                || !revisionRoundAvailable(qualityReport)) {
            return new RevisionDecision(generatedText, preservedToolTrace);
        }
        String revisionInstruction = firstRevisionInstruction(qualityReport.get("revisionSuggestions"));
        if (revisionInstruction == null || revisionInstruction.isBlank()) {
            return new RevisionDecision(generatedText, preservedToolTrace);
        }
        String reviseArgsJson = toRevisionArgsJson(generatedText, revisionInstruction, taskProfile.hardConstraints());
        ToolCallResult result = toolCallExecutionService.execute(new ToolCallRequest(
                projectId,
                task.getTaskId(),
                task.getConversationId(),
                "draft_generation",
                reviseArgsJson,
                task.getUserId(),
                traceId,
                "{}",
                "quality-revise-" + task.getTaskId()
        ));
        if (result == null || !"SUCCESS".equals(result.status())) {
            throw new IllegalStateException("quality revision execution failed");
        }
        String revisedText = extractDraftText(result.toolOutput());
        return new RevisionDecision(revisedText, appendToolTrace(preservedToolTrace, result.toolOutput()));
    }

    private StoryBibleDecision handleStoryBibleProposals(Long projectId,
                                                         AgentGenerationTask task,
                                                         AgentTaskContext taskContext,
                                                         TaskProfile taskProfile,
                                                         String generatedText,
                                                         String toolTraceJson,
                                                         String traceId,
                                                         boolean resumedFromWaitingApproval) {
        String preservedToolTrace = preserveToolTraceForRecorder(toolTraceJson);
        if (storyBibleUpdateProposalService == null
                || task == null
                || taskContext == null
                || taskContext.getChapterId() == null
                || taskProfile == null
                || !taskProfile.includeStoryBible()) {
            return StoryBibleDecision.completed(preservedToolTrace);
        }
        List<StoryBibleProposalItem> proposals = storyBibleUpdateProposalService.proposeUpdatesFromChapter(
                projectId,
                taskContext.getChapterId(),
                generatedText
        );
        if (proposals == null || proposals.isEmpty()) {
            return StoryBibleDecision.completed(preservedToolTrace);
        }
        String proposalSummaryJson = proposalSummaryJson(proposals);
        if (resumedFromWaitingApproval || maxRiskLevel(proposals) < 3 || approvalApplicationService == null) {
            return StoryBibleDecision.completed(appendToolTrace(preservedToolTrace, proposalSummaryJson));
        }
        String approvalSummaryJson = storyBibleApprovalSummaryJson(proposals);
        ApprovalRequest approvalRequest = approvalApplicationService.create(new CreateApprovalCommand(
                projectId,
                task.getTaskId(),
                "STORY_BIBLE_UPDATE",
                proposalSummaryJson,
                maxRiskLevel(proposals),
                task.getUserId()
        ), traceId);
        pendingToolInvocationRepository.save(new PendingToolInvocationSnapshot(
                approvalRequest.getId(),
                projectId,
                task.getTaskId(),
                task.getConversationId(),
                "story_bible_update",
                "{\"chapterId\":" + taskContext.getChapterId() + "}",
                "{}",
                task.getUserId(),
                traceId,
                "story-bible-approval-" + task.getTaskId(),
                "pending",
                traceId + "-story-bible-approval",
                0,
                "story-bible-call-" + approvalRequest.getId(),
                "[]",
                "[]",
                "RESUME_LOOP",
                approvalSummaryJson
        ));
        return StoryBibleDecision.waitingApproval(
                approvalRequest.getId(),
                appendToolTrace(preservedToolTrace, appendToolTrace(proposalSummaryJson, approvalSummaryJson))
        );
    }

    private boolean revisionRoundAvailable(Map<String, Object> qualityReport) {
        if (qualityReport == null) {
            return false;
        }
        Integer currentRevisionRound = integerValue(qualityReport.get("currentRevisionRound"));
        Integer maxRevisionRounds = integerValue(qualityReport.get("maxRevisionRounds"));
        if (currentRevisionRound == null || maxRevisionRounds == null) {
            return false;
        }
        return currentRevisionRound < maxRevisionRounds;
    }

    private List<SessionTodo> toSessionTodos(TodoPlanView todoPlanView) {
        if (todoPlanView == null || todoPlanView.items() == null) {
            return List.of();
        }
        java.util.ArrayList<SessionTodo> todos = new java.util.ArrayList<>();
        for (TodoPlanItemView item : todoPlanView.items()) {
            if (item == null || !item.suggestedAutoCreate()) {
                continue;
            }
            SessionTodo todo = new SessionTodo();
            todo.setTitle(stringValue(item.title()));
            todo.setDescription(stringValue(item.description()));
            todo.setSourceType(stringValue(item.sourceType()));
            todo.setTodoStatus(stringValue(item.recommendedStatus()));
            todos.add(todo);
        }
        return List.copyOf(todos);
    }

    private int maxRiskLevel(List<StoryBibleProposalItem> proposals) {
        int maxRiskLevel = 0;
        for (StoryBibleProposalItem proposal : proposals) {
            if (proposal != null && proposal.riskLevel() > maxRiskLevel) {
                maxRiskLevel = proposal.riskLevel();
            }
        }
        return maxRiskLevel;
    }

    private Map<String, Object> extractQualityReport(String toolTraceJson) {
        String normalized = normalizeToolTraceJson(toolTraceJson);
        if (normalized == null) {
            return null;
        }
        String[] fragments = normalized.split("\\r?\\n+");
        for (String fragment : fragments) {
            Map<String, Object> candidate = parseQualityReport(fragment);
            if (candidate != null) {
                return candidate;
            }
        }
        return parseQualityReport(normalized);
    }

    private Map<String, Object> parseQualityReport(String rawJson) {
        String normalized = normalizeToolTraceJson(rawJson);
        if (normalized == null) {
            return null;
        }
        try {
            Map<String, Object> json = OBJECT_MAPPER.readValue(normalized, new TypeReference<Map<String, Object>>() {
            });
            if (!json.containsKey("needsRevision")
                    || !json.containsKey("revisionSuggestions")
                    || !json.containsKey("revisionAllowed")
                    || !json.containsKey("reviewSummary")) {
                return null;
            }
            return json;
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstRevisionInstruction(Object suggestionsValue) {
        if (!(suggestionsValue instanceof List<?> suggestions) || suggestions.isEmpty()) {
            return null;
        }
        Object first = suggestions.get(0);
        if (!(first instanceof Map<?, ?> suggestionMap)) {
            return null;
        }
        return stringValue(suggestionMap.get("instruction"));
    }

    private String toRevisionArgsJson(String generatedText, String instruction, List<String> hardConstraints) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", "revise");
        payload.put("sourceText", generatedText);
        payload.put("instruction", instruction);
        payload.put("preservedConstraints", hardConstraints == null ? List.of() : hardConstraints);
        payload.put("sourceSummary", "quality revision");
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize revision args", ex);
        }
    }

    private String extractDraftText(String draftResultJson) {
        String normalized = normalizeToolTraceJson(draftResultJson);
        if (normalized == null) {
            throw new IllegalStateException("draft revision result must not be blank");
        }
        try {
            Map<String, Object> json = OBJECT_MAPPER.readValue(normalized, new TypeReference<Map<String, Object>>() {
            });
            String draftText = stringValue(json.get("draftText"));
            if (draftText == null || draftText.isBlank()) {
                throw new IllegalStateException("draft revision result missing draftText");
            }
            return draftText;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse draft revision result", ex);
        }
    }

    private String proposalSummaryJson(List<StoryBibleProposalItem> proposals) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("proposalSummary", "story bible proposals pending approval");
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (StoryBibleProposalItem proposal : proposals) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryKey", proposal.entryKey());
            item.put("entryType", proposal.entryType());
            item.put("proposedContent", proposal.proposedContent());
            item.put("canonicalStatus", proposal.canonicalStatus());
            item.put("riskLevel", proposal.riskLevel());
            item.put("sourceText", proposal.sourceText());
            item.put("sourceChapterId", proposal.sourceChapterId());
            item.put("inferenceLevel", proposal.inferenceLevel());
            items.add(item);
        }
        summary.put("items", items);
        try {
            return OBJECT_MAPPER.writeValueAsString(summary);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize story bible proposal summary", ex);
        }
    }

    private String storyBibleApprovalSummaryJson(List<StoryBibleProposalItem> proposals) {
        java.util.ArrayList<String> entryKeys = new java.util.ArrayList<>();
        for (StoryBibleProposalItem proposal : proposals) {
            if (proposal != null && proposal.entryKey() != null && !proposal.entryKey().isBlank()) {
                entryKeys.add(proposal.entryKey());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("approvalType", "STORY_BIBLE_UPDATE");
        summary.put("proposalSummary", "故事圣经更新待确认");
        summary.put("entryKeys", List.copyOf(entryKeys));
        summary.put("nextAction", "await_approval");
        try {
            return OBJECT_MAPPER.writeValueAsString(summary);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize story bible approval summary", ex);
        }
    }

    private String appendToolTrace(String existingTrace, String appendedTrace) {
        String normalizedExisting = normalizeToolTraceJson(existingTrace);
        String normalizedAppended = normalizeToolTraceJson(appendedTrace);
        if (normalizedExisting == null) {
            return normalizedAppended;
        }
        if (normalizedAppended == null) {
            return normalizedExisting;
        }
        return normalizedExisting + "\n" + normalizedAppended;
    }

    private String preserveToolTraceForRecorder(String toolTraceJson) {
        return toolTraceJson == null ? null : toolTraceJson.trim();
    }

    private record RevisionDecision(String generatedText, String toolTraceJson) {
    }

    private record StoryBibleDecision(Long approvalId, boolean waitingApproval, String toolTraceJson) {
        private static StoryBibleDecision completed(String toolTraceJson) {
            return new StoryBibleDecision(null, false, toolTraceJson);
        }

        private static StoryBibleDecision waitingApproval(Long approvalId, String toolTraceJson) {
            return new StoryBibleDecision(approvalId, true, toolTraceJson);
        }
    }

    private String normalizeToolTraceJson(String toolTraceJson) {
        if (toolTraceJson == null || toolTraceJson.isBlank()) {
            return null;
        }
        return toolTraceJson.trim();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (Object item : values) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return List.copyOf(result);
    }
 
    private void persistStructuredSnapshots(Long projectId,
                                            Long taskId,
                                            AgentTaskContext taskContext,
                                            TaskProfile taskProfile,
                                            PromptPlan promptPlan,
                                            ContextPackage contextPackage) {
        String taskProfileJson = AgentTaskRuntimeUpdater.toSnapshotJson(taskProfile);
        String promptPlanJson = AgentTaskRuntimeUpdater.toSnapshotJson(promptPlan);
        String contextPackageJson = AgentTaskRuntimeUpdater.toSnapshotJson(contextPackage);
        taskContext.setTaskProfileJson(taskProfileJson);
        taskContext.setPromptPlanJson(promptPlanJson);
        taskContext.setContextPackageJson(contextPackageJson);
        int affected = agentRepository.updateGenerationTaskSnapshots(
                projectId,
                taskId,
                taskProfileJson,
                promptPlanJson,
                contextPackageJson,
                taskContext.getActiveToolCallsSnapshot(),
                taskContext.getLastRuntimeStatus(),
                taskContext.getRecoveryCursor()
        );
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task snapshots");
        }
    }

    private void transitionToFailed(Long projectId, AgentGenerationTask task, Exception ex) {
        try {
            transitionStatus(projectId, task, AgentTaskStatus.FAILED, safeErrorMessage(ex));
        } catch (Exception transitionEx) {
            log.error("失败状态回写异常: projectId={}, taskId={}", projectId, task.getTaskId(), transitionEx);
        }
    }

    private void transitionStatus(Long projectId, AgentGenerationTask task, AgentTaskStatus targetStatus, String errorMsg) {
        taskStateMachine.assertTransition(task.getStatus(), targetStatus);
        int affected = agentRepository.updateGenerationTaskStatus(projectId, task.getTaskId(), targetStatus.value(), errorMsg);
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task status");
        }
        task.setStatus(targetStatus.value());
    }

    private List<String> extractUserMentionedEntities(String... texts) {
        Set<String> entities = new LinkedHashSet<>();
        List<String> knownEntities = List.of("林烬", "苏砚", "白檀", "城主");
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (String knownEntity : knownEntities) {
                if (text.contains(knownEntity)) {
                    entities.add(knownEntity);
                }
            }
            Matcher matcher = CJK_ENTITY_PATTERN.matcher(text);
            while (matcher.find()) {
                String candidate = matcher.group();
                if (isLikelyEntity(candidate)) {
                    entities.add(candidate);
                }
            }
        }
        return List.copyOf(entities);
    }

    private boolean isLikelyEntity(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return !candidate.contains("请")
                && !candidate.contains("当前")
                && !candidate.contains("设定")
                && !candidate.contains("继续")
                && !candidate.contains("输出")
                && !candidate.contains("核对")
                && !candidate.contains("第")
                && !candidate.contains("章节")
                && !candidate.contains("再查")
                && !candidate.contains("知识库");
    }

    private String requirePromptSnapshot(AgentGenerationTask task, String traceId) {
        String promptSnapshot = task == null ? null : task.getPromptSnapshot();
        if (promptSnapshot == null || promptSnapshot.isBlank()) {
            log.error("Agent 任务缺少有效 promptSnapshot，无法进入 preflight: taskId={}, physicalId={}, conversationId={}, traceId={}",
                    task == null ? null : task.getTaskId(),
                    task == null ? null : task.getId(),
                    task == null ? null : task.getConversationId(),
                    traceId);
            throw new IllegalStateException("task promptSnapshot must not be blank before preflight");
        }
        return promptSnapshot.trim();
    }

    private AgentLlmExecutionConfig resolvePreflightExecutionConfig(AgentGenerationTask task, String traceId) {
        IamUser iamUser = iamGateway.findUserByUserId(task.getUserId());
        if (iamUser == null || iamUser.getDirtyWorkAgentModelConfigId() == null) {
            log.error("Agent preflight 缺少 dirtywork 模型配置: taskId={}, userId={}, traceId={}",
                    task == null ? null : task.getTaskId(),
                    task == null ? null : task.getUserId(),
                    traceId);
            throw new IllegalStateException("dirty work agent model config is required before preflight");
        }
        return agentModelRoutingService.resolveExecutionConfig(task.getUserId(), iamUser.getDirtyWorkAgentModelConfigId(), traceId);
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String safeErrorMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return null;
        }
        String message = ex.getMessage().trim();
        if (message.length() <= ERROR_MSG_MAX_LENGTH) {
            return message;
        }
        String truncated = message.substring(0, ERROR_MSG_MAX_LENGTH - 3) + "...";
        log.warn("任务失败错误信息已截断: originalLength={}, truncatedLength={}, exceptionType={}",
                message.length(),
                truncated.length(),
                ex.getClass().getName());
        return truncated;
    }

    private RuntimeStatusView buildRuntimeStatusView(AgentGenerationTask task,
                                                     AgentTaskContext taskContext,
                                                     String phase,
                                                     String message,
                                                     boolean recoverable,
                                                     String nextAction,
                                                     String toolTraceJson) {
        ToolCallStatusView toolCallStatusView = resolveToolCallStatus(taskContext);
        StoryBibleApprovalView storyBibleApprovalView = resolveStoryBibleApproval(taskContext, toolTraceJson);
        TodoPlanView todoPlanView = resolveTodoPlan(taskContext, toolCallStatusView, toolTraceJson);
        return new RuntimeStatusView(
                task == null ? null : task.getTaskId(),
                task == null ? null : task.getConversationId(),
                taskContext == null ? null : taskContext.getTurnId(),
                phase,
                message,
                toolCallStatusView,
                resolveApprovalStatus(taskContext),
                storyBibleApprovalView,
                todoPlanView,
                recoverable,
                nextAction
        );
    }

    private void ensureWaitingApprovalToolSnapshot(AgentTaskContext taskContext,
                                                   AgentToolLoopIterationResult loopResult) {
        if (taskContext == null || loopResult == null) {
            return;
        }
        if (taskContext.getActiveToolCallsSnapshot() != null && !taskContext.getActiveToolCallsSnapshot().isBlank()) {
            return;
        }
        Map<String, Object> toolCallSnapshot = new LinkedHashMap<>();
        toolCallSnapshot.put("toolCallId", loopResult.approvalId() == null ? null : "approval:" + loopResult.approvalId());
        toolCallSnapshot.put("toolCode", "approval");
        toolCallSnapshot.put("toolName", "approval");
        toolCallSnapshot.put("status", "waiting_approval");
        toolCallSnapshot.put("iteration", loopResult.toolCallCount());
        toolCallSnapshot.put("argumentsPreview", null);
        toolCallSnapshot.put("output", null);
        toolCallSnapshot.put("errorMessage", null);
        taskContext.setActiveToolCallsSnapshot(AgentTaskRuntimeUpdater.toSnapshotJson(List.of(toolCallSnapshot)));
    }

    private ToolCallStatusView resolveToolCallStatus(AgentTaskContext taskContext) {
        if (taskContext == null || taskContext.getActiveToolCallsSnapshot() == null || taskContext.getActiveToolCallsSnapshot().isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> snapshots = OBJECT_MAPPER.readValue(
                    taskContext.getActiveToolCallsSnapshot(),
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            if (snapshots == null || snapshots.isEmpty()) {
                return null;
            }
            Map<String, Object> snapshot = snapshots.get(0);
            return new ToolCallStatusView(
                    stringValue(snapshot.get("toolCallId")),
                    stringValue(snapshot.get("toolCode")),
                    stringValue(snapshot.get("toolName")),
                    stringValue(snapshot.get("status")),
                    integerValue(snapshot.get("iteration")),
                    snapshot.get("argumentsPreview"),
                    snapshot.get("output"),
                    stringValue(snapshot.get("errorMessage"))
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> resolveApprovalStatus(AgentTaskContext taskContext) {
        if (taskContext == null || taskContext.getRecoveryCursor() == null || !taskContext.getRecoveryCursor().startsWith("approval:")) {
            return null;
        }
        String approvalIdToken = taskContext.getRecoveryCursor().substring("approval:".length()).trim();
        Map<String, Object> approval = new LinkedHashMap<>();
        Long approvalId = longValue(approvalIdToken);
        if (approvalId != null) {
            approval.put("approvalId", approvalId);
        }
        PendingToolInvocationSnapshot pendingSnapshot = approvalId == null ? null : pendingToolInvocationRepository.findByApprovalId(approvalId);
        if (pendingSnapshot != null) {
            if (pendingSnapshot.toolCallId() != null && !pendingSnapshot.toolCallId().isBlank()) {
                approval.put("toolCallId", pendingSnapshot.toolCallId());
            }
            if (pendingSnapshot.resumeMode() != null && !pendingSnapshot.resumeMode().isBlank()) {
                approval.put("resumeMode", pendingSnapshot.resumeMode());
            }
            Object approvalSummary = parseJsonOrRaw(pendingSnapshot.approvalSummaryJson());
            if (approvalSummary != null) {
                approval.put("approvalSummary", approvalSummary);
                if (approvalSummary instanceof Map<?, ?> summaryMap) {
                    Object approvalType = summaryMap.get("approvalType");
                    if (approvalType != null) {
                        approval.put("approvalType", String.valueOf(approvalType));
                    }
                }
            }
        }
        return approval.isEmpty() ? null : approval;
    }

    private StoryBibleApprovalView resolveStoryBibleApproval(AgentTaskContext taskContext, String toolTraceJson) {
        Map<String, Object> approval = resolveApprovalStatus(taskContext);
        Long approvalId = approval == null ? null : longValue(approval.get("approvalId"));
        String approvalType = approval == null ? null : stringValue(approval.get("approvalType"));
        Map<String, Object> persistedSummaryMap = approval == null ? null : mapValue(approval.get("approvalSummary"));
        Map<String, Object> traceSummaryMap = extractStoryBibleApprovalSummary(toolTraceJson);
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        if (persistedSummaryMap != null) {
            summaryMap.putAll(persistedSummaryMap);
        }
        if (traceSummaryMap != null) {
            summaryMap.putAll(traceSummaryMap);
        }
        if (summaryMap.isEmpty()) {
            return null;
        }
        String proposalSummary = stringValue(summaryMap.get("proposalSummary"));
        List<String> entryKeys = stringList(summaryMap.get("entryKeys"));
        String nextAction = stringValue(summaryMap.get("nextAction"));
        if (approvalType == null) {
            approvalType = stringValue(summaryMap.get("approvalType"));
        }
        if (approvalId == null && approvalType == null && proposalSummary == null && entryKeys.isEmpty() && nextAction == null) {
            return null;
        }
        return new StoryBibleApprovalView(
                approvalId,
                approvalType,
                proposalSummary,
                entryKeys,
                nextAction
        );
    }

    private TodoPlanView resolveTodoPlan(AgentTaskContext taskContext, ToolCallStatusView toolCallStatusView, String toolTraceJson) {
        TodoPlanView todoPlanFromTrace = extractTodoPlan(toolTraceJson);
        if (todoPlanFromTrace != null) {
            return todoPlanFromTrace;
        }
        if (normalizeToolCode(toolCallStatusView) != null && !"todo_planner".equals(normalizeToolCode(toolCallStatusView))) {
            return null;
        }
        TodoPlanView todoPlanFromOutput = extractTodoPlan(toolCallStatusView == null ? null : stringValue(toolCallStatusView.output()));
        if (todoPlanFromOutput != null) {
            return todoPlanFromOutput;
        }
        if (taskContext == null || taskContext.getRecoveryCursor() == null || !taskContext.getRecoveryCursor().startsWith("tool_call:todo_planner:")) {
            return null;
        }
        return extractTodoPlan(taskContext.getActiveToolCallsSnapshot());
    }

    private String normalizeToolCode(ToolCallStatusView toolCallStatusView) {
        if (toolCallStatusView == null || toolCallStatusView.toolCode() == null) {
            return null;
        }
        return toolCallStatusView.toolCode().trim().toLowerCase();
    }

    private Map<String, Object> extractStoryBibleApprovalSummary(String toolTraceJson) {
        String normalized = normalizeToolTraceJson(toolTraceJson);
        if (normalized == null) {
            return null;
        }
        String[] fragments = normalized.split("\\r?\\n+");
        for (String fragment : fragments) {
            Map<String, Object> candidate = parseStoryBibleApprovalSummary(fragment);
            if (candidate != null) {
                return candidate;
            }
        }
        return parseStoryBibleApprovalSummary(normalized);
    }

    private Map<String, Object> parseStoryBibleApprovalSummary(String rawJson) {
        String normalized = normalizeToolTraceJson(rawJson);
        if (normalized == null) {
            return null;
        }
        try {
            Map<String, Object> json = OBJECT_MAPPER.readValue(normalized, new TypeReference<Map<String, Object>>() {
            });
            if (!json.containsKey("approvalType")
                    || !json.containsKey("proposalSummary")
                    || !json.containsKey("entryKeys")
                    || !json.containsKey("nextAction")) {
                return null;
            }
            return json;
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private Object parseJsonOrRaw(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void publishDerivedReviewPhases(Long projectId,
                                            AgentGenerationTask task,
                                            AgentTaskContext taskContext,
                                            TaskProfile taskProfile) {
        if (taskProfile == null) {
            return;
        }
        if (taskProfile.includeStoryBible()) {
            syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, null, null, "story_bible_review", "phase:story_bible_review");
            taskRuntimeStatusPublisher.publishStatus(projectId,
                    buildRuntimeStatusView(task, taskContext, "story_bible_review", "正在整理故事圣经", true, "route_story_bible", null));
        }
        if (taskProfile.tools().contains("quality_review") || taskProfile.intentTags().contains(TaskIntentTag.CONTINUITY_CHECK)) {
            syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, null, null, "quality_review", "phase:quality_review");
            taskRuntimeStatusPublisher.publishStatus(projectId,
                    buildRuntimeStatusView(task, taskContext, "quality_review", "正在审查质量", true, "plan_quality_review", null));
        }
        if (taskProfile.tools().contains("todo_planner")) {
            syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, null, null, "todo_review", "phase:todo_review");
            taskRuntimeStatusPublisher.publishStatus(projectId,
                    buildRuntimeStatusView(task, taskContext, "todo_review", "正在整理待办", true, "plan_todo_review", null));
        }
    }

    private void syncRuntimeSnapshot(Long projectId,
                                     AgentGenerationTask task,
                                     AgentTaskContext taskContext,
                                     TaskProfile taskProfile,
                                     PromptPlan promptPlan,
                                     ContextPackage contextPackage,
                                     String lastRuntimeStatus,
                                     String recoveryCursor) {
        if (task == null || taskContext == null || task.getTaskId() == null) {
            return;
        }
        if (lastRuntimeStatus != null) {
            taskContext.setLastRuntimeStatus(lastRuntimeStatus);
        }
        if (recoveryCursor != null) {
            taskContext.setRecoveryCursor(recoveryCursor);
        }
        String taskProfileJson = taskProfile == null ? taskContext.getTaskProfileJson() : AgentTaskRuntimeUpdater.toSnapshotJson(taskProfile);
        String promptPlanJson = promptPlan == null ? taskContext.getPromptPlanJson() : AgentTaskRuntimeUpdater.toSnapshotJson(promptPlan);
        String contextPackageJson = contextPackage == null ? taskContext.getContextPackageJson() : AgentTaskRuntimeUpdater.toSnapshotJson(contextPackage);
        taskContext.setTaskProfileJson(taskProfileJson);
        taskContext.setPromptPlanJson(promptPlanJson);
        taskContext.setContextPackageJson(contextPackageJson);
        int affected = agentRepository.updateGenerationTaskSnapshots(
                projectId,
                task.getTaskId(),
                taskProfileJson,
                promptPlanJson,
                contextPackageJson,
                taskContext.getActiveToolCallsSnapshot(),
                taskContext.getLastRuntimeStatus(),
                taskContext.getRecoveryCursor()
        );
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task snapshots");
        }
    }
}
