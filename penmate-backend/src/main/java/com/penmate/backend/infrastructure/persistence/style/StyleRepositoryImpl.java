package com.penmate.backend.infrastructure.persistence.style;

import com.penmate.backend.domain.style.model.StyleProfile;
import com.penmate.backend.domain.style.repository.StyleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 风格仓储 MyBatis 实现。
 * <p>负责风格档案、默认风格与风格切换日志的持久化读写。</p>
 */
@Repository
public class StyleRepositoryImpl implements StyleRepository {

    private final StyleProfileMapper styleProfileMapper;

    public StyleRepositoryImpl(StyleProfileMapper styleProfileMapper) {
        this.styleProfileMapper = styleProfileMapper;
    }

    /**
     * 查询项目风格列表。
     * <p>流程：按项目ID读取风格档案集合。</p>
     */
    @Override
    public List<StyleProfile> findByProjectId(Long projectId) {
        return styleProfileMapper.findByProjectId(projectId);
    }

    /**
     * 查询单个风格档案。
     * <p>流程：按项目与风格ID双键读取风格详情。</p>
     */
    @Override
    public StyleProfile findById(Long projectId, Long styleId) {
        return styleProfileMapper.findById(projectId, styleId);
    }

    /**
     * 查询项目默认风格。
     * <p>流程：按项目读取默认标记为 true 的风格记录。</p>
     */
    @Override
    public StyleProfile findDefaultByProjectId(Long projectId) {
        return styleProfileMapper.findDefaultByProjectId(projectId);
    }

    /**
     * 新增风格档案。
     * <p>流程：将风格领域对象写入数据库。</p>
     */
    @Override
    public int insert(StyleProfile styleProfile) {
        return styleProfileMapper.insert(styleProfile);
    }

    /**
     * 更新风格档案。
     * <p>流程：按主键更新风格配置字段。</p>
     */
    @Override
    public int update(StyleProfile styleProfile) {
        return styleProfileMapper.update(styleProfile);
    }

    /**
     * 软删除风格。
     * <p>流程：标记风格为删除态而非物理删除。</p>
     */
    @Override
    public int softDelete(Long projectId, Long styleId) {
        return styleProfileMapper.softDelete(projectId, styleId);
    }

    /**
     * 清空项目默认风格标记。
     * <p>流程：将项目下当前默认标记批量置空，为新默认值设置做准备。</p>
     */
    @Override
    public int clearDefaultByProjectId(Long projectId) {
        return styleProfileMapper.clearDefaultByProjectId(projectId);
    }

    /**
     * 设置项目默认风格。
     * <p>流程：将目标风格置为默认。</p>
     */
    @Override
    public int setDefault(Long projectId, Long styleId) {
        return styleProfileMapper.setDefault(projectId, styleId);
    }

    /**
     * 写入风格切换日志。
     * <p>流程：记录切换前后风格、操作者及风险确认信息，支持审计追踪。</p>
     */
    @Override
    public int insertSwitchLog(Long switchLogId,
                               Long projectId,
                               Long fromStyleId,
                               Long toStyleId,
                               Long switchedBy,
                               boolean warningConfirmed,
                               String reason) {
        return styleProfileMapper.insertSwitchLog(switchLogId, projectId, fromStyleId, toStyleId, switchedBy, warningConfirmed, reason);
    }
}

