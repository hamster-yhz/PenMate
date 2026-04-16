package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.command.AgentCommands.ApplyGenerationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateGenerationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateMessageCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.AuditService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AgentApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentApplicationService {

    private final AgentRepository agentRepository;
    private final AuditService auditService;
    private final RealtimeEventService realtimeEventService;

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<AgentConversation> listConversations(Long projectId) {
        log.info("查询会话列表: projectId={}", projectId);
        return agentRepository.listConversations(projectId);
    }

    /**
     * 创建业务数据。
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
     * 查询列表数据。
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
     * 创建业务数据。
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
     * 创建业务数据。
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
        ensureConversation(projectId, command.conversationId());
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(projectId);
        task.setConversationId(command.conversationId());
        task.setChapterId(command.chapterId());
        task.setTaskType(command.taskType());
        task.setPromptSnapshot(command.promptSnapshot());
        task.setStyleProfileSnapshot(command.styleProfileSnapshot());
        task.setPluginSnapshot(command.pluginSnapshot());
        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());
        int affected = agentRepository.insertGenerationTask(task);
        if (affected != 1) {
            log.error("创建生成任务失败: projectId={}, conversationId={}", projectId, command.conversationId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create generation task");
        }
        realtimeEventService.publishGenerationToken(projectId, task.getId(), "开始生成", false);
        agentRepository.updateGenerationTaskStatus(projectId, task.getId(), "done", null);
        realtimeEventService.publishGenerationToken(projectId, task.getId(), "", true);
        writeAudit(traceId, command.operatorId(), "agent", "generation:create", "agent_generation_tasks",
                String.valueOf(task.getId()), command.promptSnapshot(), 201);
        log.info("创建生成任务成功: taskId={}", task.getId());
        return getGeneration(projectId, task.getId());
    }

    /**
     * 查询详情数据。
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
     * 处理业务请求。
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
        if (!"done".equals(task.getStatus()) && !"applied".equals(task.getStatus())) {
            log.warn("应用生成任务失败: projectId={}, taskId={}, status={}", projectId, taskId, task.getStatus());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Generation task is not ready for apply");
        }
        int affected = agentRepository.updateGenerationTaskStatus(projectId, taskId, "applied", null);
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

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}


