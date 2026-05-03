package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.command.AgentCommands.CreateMessageCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentMessageAppService {

    private final AgentRepository agentRepository;

    public List<AgentMessage> listMessages(Long projectId, Long conversationId) {
        log.info("查询消息列表: projectId={}, conversationId={}", projectId, conversationId);
        ensureConversation(projectId, conversationId);
        return agentRepository.listMessages(conversationId);
    }

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
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create message");
        }
        agentRepository.touchConversationLastMessage(conversationId);
        return message;
    }

    private void ensureConversation(Long projectId, Long conversationId) {
        AgentConversation conversation = agentRepository.findConversation(projectId, conversationId);
        if (conversation == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Conversation not found");
        }
    }
}
