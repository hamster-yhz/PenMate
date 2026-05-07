package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.orchestration.AgentGenerationWorkflowDispatcher;
import com.penmate.backend.application.agent.command.AgentCommands.ApplyGenerationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateGenerationCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.service.AgentTaskTransitionPolicy;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 生成任务用例应用服务。
 * <p>面向接口层提供“创建生成任务、查询任务、应用生成结果”等稳定用例入口，负责前置校验、任务落库和异步编排派发。</p>
 * <p>它不直接承担 RAG 检索、模型调用或 tool loop 细节，这些长流程职责下沉到
 * {@link com.penmate.backend.application.agent.orchestration.AgentGenerationWorkflow}。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentGenerationAppService {

    private final AgentRepository agentRepository;
    private final AgentTaskTransitionPolicy taskTransitionPolicy;
    private final AgentGenerationWorkflowDispatcher orchestrationDispatcher;
    private final AgentJsonInputNormalizer jsonInputNormalizer;
    private final BusinessIdGenerator businessIdGenerator;

    public AgentGenerationTask createGeneration(Long projectId,
                                                CreateGenerationCommand command,
                                                String traceId) {
        log.info("创建生成任务: projectId={}, conversationId={}, taskType={}", projectId, command.conversationId(), command.taskType());
        ensureConversation(projectId, command.conversationId());
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(businessIdGenerator.nextId());
        task.setProjectId(projectId);
        task.setConversationId(command.conversationId());
        task.setChapterId(command.chapterId());
        task.setModelConfigId(command.modelConfigId());
        task.setTaskType(command.taskType());
        task.setPromptSnapshot(jsonInputNormalizer.normalize(command.promptSnapshot()));
        task.setStyleProfileSnapshot(jsonInputNormalizer.normalize(command.styleProfileSnapshot()));
        task.setPluginSnapshot(jsonInputNormalizer.normalize(command.pluginSnapshot()));
        task.setTraceId(traceId);
        task.setStatus(AgentTaskStatus.PENDING.value());
        int affected = agentRepository.insertGenerationTask(task);
        if (affected != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create generation task");
        }
        orchestrationDispatcher.dispatchInitialRun(projectId, task.getTaskId(), traceId);
        return getGeneration(projectId, task.getTaskId());
    }

    public AgentGenerationTask getGeneration(Long projectId, Long taskId) {
        AgentGenerationTask task = agentRepository.findGenerationTask(projectId, taskId);
        if (task == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Generation task not found");
        }
        return task;
    }

    public AgentGenerationTask applyGeneration(Long projectId,
                                               Long taskId,
                                               ApplyGenerationCommand command,
                                               String traceId) {
        AgentGenerationTask task = getGeneration(projectId, taskId);
        AgentTaskStatus currentStatus = taskTransitionPolicy.parseStatus(task.getStatus());
        if (currentStatus == AgentTaskStatus.APPLIED) {
            return task;
        }
        taskTransitionPolicy.assertTransition(currentStatus.value(), AgentTaskStatus.APPLIED);
        int affected = agentRepository.updateGenerationTaskStatus(projectId, taskId, AgentTaskStatus.APPLIED.value(), null);
        if (affected != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to apply generation result");
        }
        return getGeneration(projectId, taskId);
    }

    private void ensureConversation(Long projectId, Long conversationId) {
        AgentConversation conversation = agentRepository.findConversation(projectId, conversationId);
        if (conversation == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Conversation not found");
        }
    }
}
