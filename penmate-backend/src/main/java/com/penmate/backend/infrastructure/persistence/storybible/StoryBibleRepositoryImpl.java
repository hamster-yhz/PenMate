package com.penmate.backend.infrastructure.persistence.storybible;

import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Story Bible 仓储 MyBatis 实现。
 * <p>负责按项目与章节边界读取当前可进入上下文构建的长期知识条目。</p>
 */
@Repository
public class StoryBibleRepositoryImpl implements StoryBibleRepository {

    private static final Logger log = LoggerFactory.getLogger(StoryBibleRepositoryImpl.class);

    private final StoryBibleMapper storyBibleMapper;

    public StoryBibleRepositoryImpl(StoryBibleMapper storyBibleMapper) {
        this.storyBibleMapper = storyBibleMapper;
    }

    /**
     * 查询指定章节下当前有效的 Story Bible 条目。
     * <p>仅返回 CANON 与 PROPOSED，显式排除 ASSUMPTION，避免主编排把未确认知识伪装成 canon。</p>
     */
    @Override
    public List<StoryBibleEntry> findActiveEntries(Long projectId, Long chapterId) {
        log.debug("Loading active story bible entries for projectId={} chapterId={}", projectId, chapterId);
        List<StoryBibleEntry> entries = storyBibleMapper.findActiveEntries(projectId, chapterId);
        log.debug("Loaded {} active story bible entries for projectId={} chapterId={}", entries.size(), projectId, chapterId);
        return entries;
    }

    @Override
    public StoryBible findByProjectId(Long projectId) {
        return storyBibleMapper.findByProjectId(projectId);
    }

    @Override
    public StoryBibleEntry findByEntryId(Long projectId, Long entryId) {
        return storyBibleMapper.findByEntryId(projectId, entryId);
    }

    @Override
    public int insert(StoryBibleEntry entry) {
        return storyBibleMapper.insert(entry);
    }

    @Override
    public int update(StoryBibleEntry entry) {
        return storyBibleMapper.update(entry);
    }

    @Override
    public int softDelete(Long projectId, Long entryId) {
        return storyBibleMapper.softDelete(projectId, entryId);
    }
}
