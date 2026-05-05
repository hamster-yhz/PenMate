package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.repository.AgentRepository;
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

    public List<AgentConversation> listConversations(Long projectId) {
        log.info("查询会话列表: projectId={}", projectId);
        return agentRepository.listConversations(projectId);
    }

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
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create conversation");
        }
        return conversation;
    }
}
