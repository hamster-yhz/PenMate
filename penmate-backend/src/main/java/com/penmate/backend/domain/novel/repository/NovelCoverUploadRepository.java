package com.penmate.backend.domain.novel.repository;

import com.penmate.backend.domain.novel.model.NovelCoverUploadSession;
import com.penmate.backend.domain.novel.model.NovelProject;
import java.time.Instant;
import java.util.List;

public interface NovelCoverUploadRepository {
    int insert(NovelCoverUploadSession session);

    NovelCoverUploadSession findById(Long uploadId);

    NovelCoverUploadSession findByIdForUpdate(Long uploadId);

    NovelCoverUploadSession findLatestByProject(Long projectId);

    NovelCoverUploadSession findLatestCompletedByProject(Long projectId);

    List<NovelCoverUploadSession> findExpiredPending(Instant now);

    NovelProject lockProject(Long projectId);

    int setPendingUpload(Long projectId, Long ownerUserId, Long uploadId);

    int markProcessing(Long uploadId, Double cropX, Double cropY, Double cropWidth, Double cropHeight,
                       Integer imageWidth, Integer imageHeight, String displayObjectKey,
                       String thumbnailObjectKey);

    int retry(Long uploadId);

    int markCompleted(Long uploadId);

    int markFailed(Long uploadId, String errorCode, String errorMessage);

    int markSuperseded(Long uploadId);

    int markExpired(Long uploadId);

    int applyCover(Long projectId, Long uploadId, String originalObjectKey,
                   String displayObjectKey, String thumbnailObjectKey);

    int clearCover(Long projectId, Long ownerUserId);
}
