package com.penmate.backend.domain.storybible.repository;

import com.penmate.backend.domain.storybible.model.StoryBibleEntry;

import java.util.List;

/**
 * Story Bible 仓储接口。
 * <p>负责为单 Main Orchestrator 提供可按章节裁剪的长期知识条目。</p>
 */
public interface StoryBibleRepository {

    /**
     * 查询项目在指定章节边界下当前生效的 Story Bible 条目。
     * <p>仅返回可进入上下文构建的 CANON 与 PROPOSED 条目。</p>
     */
    List<StoryBibleEntry> findActiveEntries(Long projectId, Long chapterId);
}
