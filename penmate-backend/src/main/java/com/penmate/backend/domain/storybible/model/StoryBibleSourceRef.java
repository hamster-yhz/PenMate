package com.penmate.backend.domain.storybible.model;

import lombok.Data;

/**
 * Story Bible 条目来源引用。
 * <p>用于记录条目来自章节、卡片或其他证据来源，便于审批与追踪。</p>
 */
@Data
public class StoryBibleSourceRef {

    /** 来源类型：chapter/card/rag/manual。 */
    private String refType;
    /** 来源业务 ID。 */
    private Long refId;
    /** 来源说明。 */
    private String note;
}
