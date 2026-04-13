package com.penmate.backend.infrastructure.persistence.style;

import com.penmate.backend.domain.style.model.StyleProfile;
import com.penmate.backend.domain.style.repository.StyleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StyleRepositoryImpl implements StyleRepository {

    private final StyleProfileMapper styleProfileMapper;

    public StyleRepositoryImpl(StyleProfileMapper styleProfileMapper) {
        this.styleProfileMapper = styleProfileMapper;
    }

    @Override
    public List<StyleProfile> findByProjectId(Long projectId) {
        return styleProfileMapper.findByProjectId(projectId);
    }

    @Override
    public StyleProfile findById(Long projectId, Long styleId) {
        return styleProfileMapper.findById(projectId, styleId);
    }

    @Override
    public StyleProfile findDefaultByProjectId(Long projectId) {
        return styleProfileMapper.findDefaultByProjectId(projectId);
    }

    @Override
    public int insert(StyleProfile styleProfile) {
        return styleProfileMapper.insert(styleProfile);
    }

    @Override
    public int update(StyleProfile styleProfile) {
        return styleProfileMapper.update(styleProfile);
    }

    @Override
    public int softDelete(Long projectId, Long styleId) {
        return styleProfileMapper.softDelete(projectId, styleId);
    }

    @Override
    public int clearDefaultByProjectId(Long projectId) {
        return styleProfileMapper.clearDefaultByProjectId(projectId);
    }

    @Override
    public int setDefault(Long projectId, Long styleId) {
        return styleProfileMapper.setDefault(projectId, styleId);
    }

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

