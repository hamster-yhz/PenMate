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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AgentApplicationService {

    private final AgentRepository agentRepository;
    private final AuditService auditService;
    private final RealtimeEventService realtimeEventService;

    public AgentApplicationService(AgentRepository agentRepository,
                                   AuditService auditService,
                                   RealtimeEventService realtimeEventService) {
        this.agentRepository = agentRepository;
        this.auditService = auditService;
        this.realtimeEventService = realtimeEventService;
    }

    public List<AgentConversation> listConversations(Long projectId) {
        return agentRepository.listConversations(projectId);
    }

    public AgentConversation createConversation(Long projectId,
                                                CreateConversationCommand command,
                                                String traceId) {
        AgentConversation conversation = new AgentConversation();
        conversation.setProjectId(projectId);
        conversation.setUserId(command.userId());
        conversation.setTitle(command.title());
        conversation.setContextScopeJson(command.contextScopeJson());
        conversation.setStatus(command.status() == null || command.status().isBlank() ? "active" : command.status());
        int affected = agentRepository.insertConversation(conversation);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create conversation");
        }
        writeAudit(traceId, command.operatorId(), "agent", "conversation:create", "agent_conversations",
                String.valueOf(conversation.getId()), command.contextScopeJson(), 201);
        return conversation;
    }

    public List<AgentMessage> listMessages(Long projectId, Long conversationId) {
        ensureConversation(projectId, conversationId);
        return agentRepository.listMessages(conversationId);
    }

    public AgentMessage createMessage(Long projectId,
                                      Long conversationId,
                                      CreateMessageCommand command,
                                      String traceId) {
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
            throw new IllegalArgumentException("Failed to create message");
        }
        agentRepository.touchConversationLastMessage(conversationId);
        writeAudit(traceId, command.operatorId(), "agent", "message:create", "agent_messages",
                String.valueOf(message.getId()), command.contentMd(), 201);
        return message;
    }

    public AgentGenerationTask createGeneration(Long projectId,
                                                CreateGenerationCommand command,
                                                String traceId) {
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
            throw new IllegalArgumentException("Failed to create generation task");
        }
        realtimeEventService.publishGenerationToken(projectId, task.getId(), "开始生成", false);
        agentRepository.updateGenerationTaskStatus(projectId, task.getId(), "done", null);
        realtimeEventService.publishGenerationToken(projectId, task.getId(), "", true);
        writeAudit(traceId, command.operatorId(), "agent", "generation:create", "agent_generation_tasks",
                String.valueOf(task.getId()), command.promptSnapshot(), 201);
        return getGeneration(projectId, task.getId());
    }

    public AgentGenerationTask getGeneration(Long projectId, Long taskId) {
        AgentGenerationTask task = agentRepository.findGenerationTask(projectId, taskId);
        if (task == null) {
            throw new IllegalArgumentException("Generation task not found");
        }
        return task;
    }

    public AgentGenerationTask applyGeneration(Long projectId,
                                               Long taskId,
                                               ApplyGenerationCommand command,
                                               String traceId) {
        AgentGenerationTask task = getGeneration(projectId, taskId);
        if (!"done".equals(task.getStatus()) && !"applied".equals(task.getStatus())) {
            throw new IllegalArgumentException("Generation task is not ready for apply");
        }
        int affected = agentRepository.updateGenerationTaskStatus(projectId, taskId, "applied", null);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to apply generation result");
        }
        writeAudit(traceId, command.operatorId(), "agent", "generation:apply", "agent_generation_tasks",
                String.valueOf(taskId), command.applyNote(), 200);
        return getGeneration(projectId, taskId);
    }

    private void ensureConversation(Long projectId, Long conversationId) {
        AgentConversation conversation = agentRepository.findConversation(projectId, conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found");
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

