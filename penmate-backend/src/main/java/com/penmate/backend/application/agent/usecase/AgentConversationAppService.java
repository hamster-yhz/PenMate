package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public List<AgentConversation> listConversations(Long projectId) {
        log.info("查询会话列表: projectId={}", projectId);
        return agentRepository.listConversations(projectId);
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

    private String normalizeSessionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase();
    }
}
