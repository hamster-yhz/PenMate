package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent 会话用例应用服务。
 * <p>负责会话列表查询与会话创建这两个直接面向接口层的用例，不承担消息编排或生成任务流程。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentConversationAppService {

    private final AgentRepository agentRepository;
    private final BusinessIdGenerator businessIdGenerator;

    public List<AgentConversation> listConversations(Long projectId, Long userId, boolean deleted) {
        log.info("查询会话列表: projectId={}, userId={}, deleted={}", projectId, userId, deleted);
        return agentRepository.listConversations(projectId, userId, deleted).stream()
                .peek(value -> value.setLastRunStatus(
                        agentRepository.findLatestRunStatus(value.getConversationId())))
                .toList();
    }

    public AgentConversation createConversation(Long projectId,
                                                CreateConversationCommand command,
                                                String traceId) {
        log.info("创建会话: projectId={}, userId={}, title={}", projectId, command.userId(), command.title());
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId(businessIdGenerator.nextId());
        conversation.setProjectId(projectId);
        conversation.setUserId(command.userId());
        conversation.setTitle(command.title());
        conversation.setStatus(normalizeSessionStatus(command.status()));
        int affected = agentRepository.insertConversation(conversation);
        if (affected != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create conversation");
        }
        return conversation;
    }

    @Transactional
    public AgentConversation renameConversation(Long projectId, Long conversationId, Long userId, String rawTitle) {
        AgentConversation conversation = requireOwned(projectId, conversationId, userId, false);
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty() || title.length() > 80) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest(
                    "Session title must contain 1 to 80 characters");
        }
        if (agentRepository.updateConversationTitle(projectId, conversationId, userId, title) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("Session title update failed");
        }
        conversation.setTitle(title);
        return conversation;
    }

    @Transactional
    public void deleteConversation(Long projectId, Long conversationId, Long userId) {
        requireOwned(projectId, conversationId, userId, false);
        if (agentRepository.countActiveRuns(conversationId) > 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Stop the active run before deleting this session");
        }
        if (agentRepository.softDeleteConversation(projectId, conversationId, userId) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("Session deletion failed");
        }
    }

    @Transactional
    public AgentConversation restoreConversation(Long projectId, Long conversationId, Long userId) {
        AgentConversation conversation = requireOwned(projectId, conversationId, userId, true);
        if (conversation.getDeletedAt() == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("Session is not deleted");
        }
        if (agentRepository.restoreConversation(projectId, conversationId, userId) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("Session restore failed");
        }
        conversation.setDeletedAt(null);
        return conversation;
    }

    public AgentConversation requireOwned(Long projectId, Long conversationId, Long userId, boolean includeDeleted) {
        AgentConversation conversation = includeDeleted
                ? agentRepository.findConversationIncludingDeleted(projectId, conversationId)
                : agentRepository.findConversation(projectId, conversationId);
        if (conversation == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("Session not found");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw com.penmate.backend.application.common.exception.BusinessException.forbidden("Session access denied");
        }
        return conversation;
    }

    private String normalizeSessionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase();
    }
}
