package com.penmate.backend.domain.agent.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 智能体会话实体。
 */
public class AgentConversation {
    /** 会话主键 ID。 */
    private Long id;
    /** 关联的小说项目 ID。 */
    private Long projectId;
    /** 发起会话的用户 ID。 */
    private Long userId;
    /** 会话标题。 */
    private String title;
    /** 会话上下文范围快照（JSON）。 */
    private String contextScopeJson;
    /** 最近一条消息的产生时间。 */
    private LocalDateTime lastMessageAt;
    /** 会话状态（如启用、归档）。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间，为空表示未删除。 */
    private LocalDateTime deletedAt;

}

