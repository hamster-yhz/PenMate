package com.penmate.backend.domain.agent.model;

import lombok.Data;
import java.time.Instant;

@Data
/**
 * 智能体会话实体。
 */
public class AgentConversation {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 会话业务 ID。 */
    private Long conversationId;
    /** 关联的小说项目业务 ID。 */
    private Long projectId;
    /** 发起会话的用户业务 ID。 */
    private Long userId;
    /** 会话标题。 */
    private String title;
    /** 会话上下文范围快照（JSON）。 */
    private String contextScopeJson;
    /** 最近一条消息的产生时间。 */
    private Instant lastMessageAt;
    /** 会话状态（如启用、归档）。 */
    private String status;
    /** 创建时间。 */
    private Instant createdAt;
    /** 最后更新时间。 */
    private Instant updatedAt;
    /** 逻辑删除时间，为空表示未删除。 */
    private Instant deletedAt;

}

