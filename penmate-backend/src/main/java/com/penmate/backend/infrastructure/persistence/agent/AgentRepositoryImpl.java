package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent 仓储 MyBatis 实现。
 * <p>负责 Agent 会话、消息、生成任务等聚合的数据库读写，并保持领域仓储接口与 Mapper SQL 之间的映射一致性。</p>
 */
@Repository
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper agentMapper;

    public AgentRepositoryImpl(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    /**
     * 查询项目会话列表。
     * <p>流程：调用 Mapper 按项目ID读取会话集合。</p>
     */
    @Override
    public List<AgentConversation> listConversations(Long projectId) {
        return agentMapper.listConversations(projectId);
    }

    /**
     * 查询单个会话。
     * <p>流程：按项目与会话双键查询，确保归属正确。</p>
     */
    @Override
    public AgentConversation findConversation(Long projectId, Long conversationId) {
        return agentMapper.findConversation(projectId, conversationId);
    }

    /**
     * 新增会话记录。
     * <p>流程：将会话领域对象写入数据库。</p>
     */
    @Override
    public int insertConversation(AgentConversation conversation) {
        return agentMapper.insertConversation(conversation);
    }

    /**
     * 查询会话消息列表。
     * <p>流程：按会话ID读取消息明细。</p>
     */
    @Override
    public List<AgentMessage> listMessages(Long conversationId) {
        return agentMapper.listMessages(conversationId);
    }

    /**
     * 计算下一条消息序号。
     * <p>流程：读取当前最大序号并 +1，供消息追加时保持有序。</p>
     */
    @Override
    public int nextMessageSeq(Long conversationId) {
        return agentMapper.maxMessageSeq(conversationId) + 1;
    }

    /**
     * 新增消息记录。
     * <p>流程：调用 Mapper 插入消息实体。</p>
     */
    @Override
    public int insertMessage(AgentMessage message) {
        return agentMapper.insertMessage(message);
    }

    /**
     * 更新会话最近消息时间。
     * <p>流程：触发会话“最后活跃时间”刷新。</p>
     */
    @Override
    public int touchConversationLastMessage(Long conversationId) {
        return agentMapper.touchConversationLastMessage(conversationId);
    }

    /**
     * 新增生成任务记录。
     * <p>流程：落库任务快照，进入后续状态机执行。</p>
     */
    @Override
    public int insertGenerationTask(AgentGenerationTask task) {
        return agentMapper.insertGenerationTask(task);
    }

    /**
     * 查询生成任务详情。
     * <p>流程：按项目与任务ID读取任务状态与结果。</p>
     */
    @Override
    public AgentGenerationTask findGenerationTask(Long projectId, Long taskId) {
        return agentMapper.findGenerationTask(projectId, taskId);
    }

    /**
     * 更新生成任务状态。
     * <p>流程：写入任务状态与错误信息，供编排与前端轮询消费。</p>
     */
    @Override
    public int updateGenerationTaskStatus(Long projectId, Long taskId, String status, String errorMsg) {
        return agentMapper.updateGenerationTaskStatus(projectId, taskId, status, errorMsg);
    }

    /**
     * 更新生成任务运行时信息。
     * <p>流程：持久化 token 用量、成本统计与追踪ID。</p>
     */
    @Override
    public int updateGenerationTaskRuntime(Long projectId, Long taskId, String tokenUsageJson, String costJson, String traceId) {
        return agentMapper.updateGenerationTaskRuntime(projectId, taskId, tokenUsageJson, costJson, traceId);
    }
}
