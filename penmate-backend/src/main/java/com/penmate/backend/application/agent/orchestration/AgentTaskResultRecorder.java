package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 结果消息落库器。
 * <p>负责把生成工作流产出的最终 assistant 文本转换为会话消息并写入仓储，同时刷新会话最后消息指针。</p>
 * <p>该类只处理“结果消息持久化”这一单一职责，不负责任务状态流转、事件发布或模型调用。</p>
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
