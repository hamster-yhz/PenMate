package com.penmate.backend.application.agent;

import cn.hutool.json.JSONUtil;
import com.penmate.backend.application.agent.json.AgentJsons;
import com.penmate.backend.application.agent.command.AgentCommands.ApplyGenerationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateGenerationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateMessageCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能体应用服务。
 * <p>负责会话、消息、生成任务的创建与查询，并在关键节点写入审计与实时事件。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentApplicationService {

    private final AgentRepository agentRepository;
    private final AgentTaskStateMachine taskStateMachine;
    private final AgentOrchestrationDispatcher orchestrationDispatcher;

    /**
     * 查询项目下会话列表。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<AgentConversation> listConversations(Long projectId) {
        log.info("查询会话列表: projectId={}", projectId);
        return agentRepository.listConversations(projectId);
    }

    /**
     * 在项目下创建会话。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public AgentConversation createConversation(Long projectId,
                                                CreateConversationCommand command,
                                                String traceId) {
        log.info("创建会话: projectId={}, userId={}, title={}", projectId, command.userId(), command.title());
        AgentConversation conversation = new AgentConversation();
        conversation.setProjectId(projectId);
        conversation.setUserId(command.userId());
        conversation.setTitle(command.title());
        conversation.setContextScopeJson(command.contextScopeJson());
        conversation.setStatus(command.status() == null || command.status().isBlank() ? "active" : command.status());
        int affected = agentRepository.insertConversation(conversation);
        if (affected != 1) {
            log.error("创建会话失败: projectId={}, userId={}", projectId, command.userId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create conversation");
        }
        writeAudit(traceId, command.operatorId(), "agent", "conversation:create", "agent_conversations",
                String.valueOf(conversation.getId()), command.contextScopeJson(), 201);
        log.info("创建会话成功: conversationId={}", conversation.getId());
        return conversation;
    }

    /**
     * 查询指定会话的消息列表。
     *
     * @param projectId 入参：projectId
     * @param conversationId 入参：conversationId
     * @return 出参：处理结果
     */
    public List<AgentMessage> listMessages(Long projectId, Long conversationId) {
        log.info("查询消息列表: projectId={}, conversationId={}", projectId, conversationId);
        ensureConversation(projectId, conversationId);
        return agentRepository.listMessages(conversationId);
    }

    /**
     * 在指定会话下创建消息。
     *
     * @param projectId 入参：projectId
     * @param conversationId 入参：conversationId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public AgentMessage createMessage(Long projectId,
                                      Long conversationId,
                                      CreateMessageCommand command,
                                      String traceId) {
        log.info("创建消息: projectId={}, conversationId={}, role={}", projectId, conversationId, command.role());
        ensureConversation(projectId, conversationId);
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole(command.role());
        message.setUserMessageType(command.userMessageType());
        message.setContentMd(command.contentMd());
        message.setAttachmentsJson(command.attachmentsJson());
        message.setToolCallsJson(command.toolCallsJson());
        message.setSeqNo(agentRepository.nextMessageSeq(conversationId));
        int affected = agentRepository.insertMessage(message);
        if (affected != 1) {
            log.error("创建消息失败: projectId={}, conversationId={}", projectId, conversationId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create message");
        }
        agentRepository.touchConversationLastMessage(conversationId);
        writeAudit(traceId, command.operatorId(), "agent", "message:create", "agent_messages",
                String.valueOf(message.getId()), command.contentMd(), 201);
        log.info("创建消息成功: messageId={}, seqNo={}", message.getId(), message.getSeqNo());
        return message;
    }

    /**
     * 创建文本生成任务。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public AgentGenerationTask createGeneration(Long projectId,
                                                CreateGenerationCommand command,
                                                String traceId) {
        log.info("创建生成任务: projectId={}, conversationId={}, taskType={}", projectId, command.conversationId(), command.taskType());
        // 编排前置校验：任务必须绑定到已存在会话，避免后续异步链路找不到上下文。
        ensureConversation(projectId, command.conversationId());
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(projectId);
        task.setConversationId(command.conversationId());
        task.setChapterId(command.chapterId());
        // 完全显式模式：模型配置必须由前端显式传入，不再读取项目默认策略。
        task.setModelConfigId(command.modelConfigId());
        task.setTaskType(command.taskType());
        task.setPromptSnapshot(normalizeJsonField(command.promptSnapshot()));
        task.setStyleProfileSnapshot(normalizeJsonField(command.styleProfileSnapshot()));
        task.setPluginSnapshot(normalizeJsonField(command.pluginSnapshot()));
        task.setTraceId(traceId);
        task.setStatus(AgentTaskStatus.PENDING.value());
        int affected = agentRepository.insertGenerationTask(task);
        if (affected != 1) {
            log.error("创建生成任务失败: projectId={}, conversationId={}", projectId, command.conversationId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create generation task");
        }
        writeAudit(traceId, command.operatorId(), "agent", "generation:create", "agent_generation_tasks",
                String.valueOf(task.getId()), command.promptSnapshot(), 201);
        // 异步分发执行，创建接口快速返回，避免同步阻塞模型调用。
        orchestrationDispatcher.dispatch(projectId, task.getId(), traceId);
        log.info("创建生成任务成功: taskId={}", task.getId());
        // 返回最新任务快照，前端可立刻感知 pending/running 等状态。
        return getGeneration(projectId, task.getId());
    }

    /**
     * 查询生成任务详情。
     *
     * @param projectId 入参：projectId
     * @param taskId 入参：taskId
     * @return 出参：处理结果
     */
    public AgentGenerationTask getGeneration(Long projectId, Long taskId) {
        AgentGenerationTask task = agentRepository.findGenerationTask(projectId, taskId);
        if (task == null) {
            log.warn("查询生成任务失败: projectId={}, taskId={}, reason=not_found", projectId, taskId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Generation task not found");
        }
        log.info("查询生成任务成功: projectId={}, taskId={}, status={}", projectId, taskId, task.getStatus());
        return task;
    }

    /**
     * 应用生成结果。
     *
     * @param projectId 入参：projectId
     * @param taskId 入参：taskId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public AgentGenerationTask applyGeneration(Long projectId,
                                               Long taskId,
                                               ApplyGenerationCommand command,
                                               String traceId) {
        log.info("应用生成任务: projectId={}, taskId={}", projectId, taskId);
        AgentGenerationTask task = getGeneration(projectId, taskId);
        AgentTaskStatus currentStatus = taskStateMachine.parseStatus(task.getStatus());
        // 幂等保护：重复应用已落地任务直接返回，不重复写库。
        if (currentStatus == AgentTaskStatus.APPLIED) {
            return task;
        }
        // 只允许 done -> applied，其他状态会被状态机拒绝。
        taskStateMachine.assertTransition(currentStatus.value(), AgentTaskStatus.APPLIED);
        int affected = agentRepository.updateGenerationTaskStatus(projectId, taskId, AgentTaskStatus.APPLIED.value(), null);
        if (affected != 1) {
            log.error("应用生成任务失败: projectId={}, taskId={}, reason=update_failed", projectId, taskId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to apply generation result");
        }
        writeAudit(traceId, command.operatorId(), "agent", "generation:apply", "agent_generation_tasks",
                String.valueOf(taskId), command.applyNote(), 200);
        log.info("应用生成任务成功: projectId={}, taskId={}", projectId, taskId);
        return getGeneration(projectId, taskId);
    }

    private void ensureConversation(Long projectId, Long conversationId) {
        AgentConversation conversation = agentRepository.findConversation(projectId, conversationId);
        if (conversation == null) {
            log.warn("会话不存在: projectId={}, conversationId={}", projectId, conversationId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Conversation not found");
        }
    }

    /**
     * Normalize user input to a value MySQL JSON columns can accept.
     * - blank => null
     * - valid JSON => keep as-is
     * - plain text => encode as JSON string
     */
    private String normalizeJsonField(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String trimmed = rawValue.trim();
        try {
            if (trimmed.startsWith("{")) {
                return AgentJsons.toJson(AgentJsons.parseObj(trimmed));
            }
            if (trimmed.startsWith("[")) {
                return AgentJsons.toJson(AgentJsons.parseArray(trimmed));
            }
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                return trimmed;
            }
            if ("true".equals(trimmed) || "false".equals(trimmed) || "null".equals(trimmed)
                    || trimmed.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?")) {
                return trimmed;
            }
        } catch (Exception ignored) {
        }
        return JSONUtil.quote(rawValue);
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // 审计模块已移除
    }
}

