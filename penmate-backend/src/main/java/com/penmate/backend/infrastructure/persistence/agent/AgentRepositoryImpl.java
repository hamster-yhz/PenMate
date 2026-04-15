package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AgentRepositoryImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper agentMapper;

    public AgentRepositoryImpl(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public List<AgentConversation> listConversations(Long projectId) {
        return agentMapper.listConversations(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param conversationId 入参：conversationId
     * @return 出参：处理结果
     */
    @Override
    public AgentConversation findConversation(Long projectId, Long conversationId) {
        return agentMapper.findConversation(projectId, conversationId);
    }

    /**
     * 处理业务请求。
     *
     * @param conversation 入参：conversation
     * @return 出参：处理结果
     */
    @Override
    public int insertConversation(AgentConversation conversation) {
        return agentMapper.insertConversation(conversation);
    }

    /**
     * 查询列表数据。
     *
     * @param conversationId 入参：conversationId
     * @return 出参：处理结果
     */
    @Override
    public List<AgentMessage> listMessages(Long conversationId) {
        return agentMapper.listMessages(conversationId);
    }

    /**
     * 处理业务请求。
     *
     * @param conversationId 入参：conversationId
     * @return 出参：处理结果
     */
    @Override
    public int nextMessageSeq(Long conversationId) {
        return agentMapper.maxMessageSeq(conversationId) + 1;
    }

    /**
     * 处理业务请求。
     *
     * @param message 入参：message
     * @return 出参：处理结果
     */
    @Override
    public int insertMessage(AgentMessage message) {
        return agentMapper.insertMessage(message);
    }

    /**
     * 处理业务请求。
     *
     * @param conversationId 入参：conversationId
     * @return 出参：处理结果
     */
    @Override
    public int touchConversationLastMessage(Long conversationId) {
        return agentMapper.touchConversationLastMessage(conversationId);
    }

    /**
     * 处理业务请求。
     *
     * @param task 入参：task
     * @return 出参：处理结果
     */
    @Override
    public int insertGenerationTask(AgentGenerationTask task) {
        return agentMapper.insertGenerationTask(task);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param taskId 入参：taskId
     * @return 出参：处理结果
     */
    @Override
    public AgentGenerationTask findGenerationTask(Long projectId, Long taskId) {
        return agentMapper.findGenerationTask(projectId, taskId);
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param taskId 入参：taskId
     * @param status 入参：status
     * @param errorMsg 入参：errorMsg
     * @return 出参：处理结果
     */
    @Override
    public int updateGenerationTaskStatus(Long projectId, Long taskId, String status, String errorMsg) {
        return agentMapper.updateGenerationTaskStatus(projectId, taskId, status, errorMsg);
    }
}
