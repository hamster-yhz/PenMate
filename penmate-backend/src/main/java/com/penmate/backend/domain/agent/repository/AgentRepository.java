package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskResult;

import java.util.List;

public interface AgentRepository {

    List<AgentConversation> listConversations(Long projectId);

    AgentConversation findConversation(Long projectId, Long conversationId);

    int insertConversation(AgentConversation conversation);

    List<AgentMessage> listMessages(Long conversationId);

    int nextMessageSeq(Long conversationId);

    int insertMessage(AgentMessage message);

    int touchConversationLastMessage(Long conversationId);

    int insertGenerationTask(AgentGenerationTask task);

    AgentGenerationTask findGenerationTask(Long projectId, Long taskId);

    com.penmate.backend.domain.agent.model.AgentTaskContext findTaskContext(Long taskId);

    int updateGenerationTaskStatus(Long projectId, Long taskId, String status, String errorMsg);

    int updateGenerationTaskActiveApproval(Long projectId, Long taskId, Long approvalId);

    int updateGenerationTaskRuntime(Long projectId, Long taskId, String tokenUsageJson, String costJson, String traceId);

    int updateGenerationTaskSnapshots(Long projectId,
                                      Long taskId,
                                      String taskProfileJson,
                                      String promptPlanJson,
                                      String contextPackageJson,
                                      String activeToolCallsSnapshot,
                                      String lastRuntimeStatus,
                                      String recoveryCursor);

    int insertTaskResult(AgentTaskResult taskResult);

    int updateGenerationTaskResultLink(Long projectId, Long taskId, Long resultId);
}

