package com.penmate.backend.domain.style.repository;

import com.penmate.backend.domain.style.model.StyleProfile;

import java.util.List;

public interface StyleRepository {

    List<StyleProfile> findByProjectId(Long projectId);

    StyleProfile findById(Long projectId, Long styleId);

    StyleProfile findDefaultByProjectId(Long projectId);

    int insert(StyleProfile styleProfile);

    int update(StyleProfile styleProfile);

    int softDelete(Long projectId, Long styleId);

    int clearDefaultByProjectId(Long projectId);

    int setDefault(Long projectId, Long styleId);

    int insertSwitchLog(Long switchLogId,
                        Long projectId,
                        Long fromStyleId,
                        Long toStyleId,
                        Long switchedBy,
                        boolean warningConfirmed,
                        String reason);
}

