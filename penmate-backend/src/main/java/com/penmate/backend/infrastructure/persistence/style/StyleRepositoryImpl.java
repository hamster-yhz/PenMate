package com.penmate.backend.infrastructure.persistence.style;

import com.penmate.backend.domain.style.model.StyleProfile;
import com.penmate.backend.domain.style.repository.StyleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * StyleRepositoryImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class StyleRepositoryImpl implements StyleRepository {

    private final StyleProfileMapper styleProfileMapper;

    public StyleRepositoryImpl(StyleProfileMapper styleProfileMapper) {
        this.styleProfileMapper = styleProfileMapper;
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public List<StyleProfile> findByProjectId(Long projectId) {
        return styleProfileMapper.findByProjectId(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @return 出参：处理结果
     */
    @Override
    public StyleProfile findById(Long projectId, Long styleId) {
        return styleProfileMapper.findById(projectId, styleId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public StyleProfile findDefaultByProjectId(Long projectId) {
        return styleProfileMapper.findDefaultByProjectId(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param styleProfile 入参：styleProfile
     * @return 出参：处理结果
     */
    @Override
    public int insert(StyleProfile styleProfile) {
        return styleProfileMapper.insert(styleProfile);
    }

    /**
     * 更新业务数据。
     *
     * @param styleProfile 入参：styleProfile
     * @return 出参：处理结果
     */
    @Override
    public int update(StyleProfile styleProfile) {
        return styleProfileMapper.update(styleProfile);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @return 出参：处理结果
     */
    @Override
    public int softDelete(Long projectId, Long styleId) {
        return styleProfileMapper.softDelete(projectId, styleId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public int clearDefaultByProjectId(Long projectId) {
        return styleProfileMapper.clearDefaultByProjectId(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @return 出参：处理结果
     */
    @Override
    public int setDefault(Long projectId, Long styleId) {
        return styleProfileMapper.setDefault(projectId, styleId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param fromStyleId 入参：fromStyleId
     * @param toStyleId 入参：toStyleId
     * @param switchedBy 入参：switchedBy
     * @param warningConfirmed 入参：warningConfirmed
     * @param reason 入参：reason
     * @return 出参：处理结果
     */
    @Override
    public int insertSwitchLog(Long projectId,
                               Long fromStyleId,
                               Long toStyleId,
                               Long switchedBy,
                               boolean warningConfirmed,
                               String reason) {
        return styleProfileMapper.insertSwitchLog(projectId, fromStyleId, toStyleId, switchedBy, warningConfirmed, reason);
    }
}

