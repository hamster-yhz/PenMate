package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 持久化生成完成后的 assistant 结果消息。
 */
@Component
@RequiredArgsConstructor
public class AgentTaskResultRecorder {

    private final AgentRepository agentRepository;

    public void recordAssistantResult(AgentGenerationTask task, String generatedText) {
        AgentMessage assistantMessage = new AgentMessage();
        assistantMessage.setConversationId(task.getConversationId());
        assistantMessage.setRole("assistant");
        assistantMessage.setUserMessageType("GENERATION_RESULT");
        assistantMessage.setContentMd(generatedText);
        assistantMessage.setAttachmentsJson("[]");
        assistantMessage.setToolCallsJson("[]");
        assistantMessage.setSeqNo(agentRepository.nextMessageSeq(task.getConversationId()));
        agentRepository.insertMessage(assistantMessage);
        agentRepository.touchConversationLastMessage(task.getConversationId());
    }
}
