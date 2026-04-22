package com.penmate.backend.domain.agent.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 智能体会话消息实体。
 */
public class AgentMessage {
    /** 消息主键 ID。 */
    private Long id;
    /** 所属会话 ID。 */
    private Long conversationId;
    /** 消息角色（user/assistant/system）。 */
    private String role;
    /** 用户消息类型（文本、指令等）。 */
    private String userMessageType;
    /** Markdown 正文内容。 */
    private String contentMd;
    /** 附件元信息（JSON）。 */
    private String attachmentsJson;
    /** 工具调用记录（JSON）。 */
    private String toolCallsJson;
    /** 会话内顺序号。 */
    private Integer seqNo;
    /** 消息创建时间。 */
    private LocalDateTime createdAt;

}

