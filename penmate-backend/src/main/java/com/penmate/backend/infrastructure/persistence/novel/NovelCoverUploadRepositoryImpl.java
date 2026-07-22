package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelCoverUploadSession;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelCoverUploadRepository;
import org.springframework.stereotype.Repository;

@Repository
public class NovelCoverUploadRepositoryImpl implements NovelCoverUploadRepository {
    private final NovelCoverUploadMapper mapper;

    public NovelCoverUploadRepositoryImpl(NovelCoverUploadMapper mapper) {
        this.mapper = mapper;
    }

    @Override public int insert(NovelCoverUploadSession session) { return mapper.insert(session); }
    @Override public NovelCoverUploadSession findById(Long uploadId) { return mapper.findById(uploadId); }
    @Override public NovelCoverUploadSession findByIdForUpdate(Long uploadId) { return mapper.findByIdForUpdate(uploadId); }
    @Override public NovelCoverUploadSession findLatestByProject(Long projectId) { return mapper.findLatestByProject(projectId); }
    @Override public NovelCoverUploadSession findLatestCompletedByProject(Long projectId) { return mapper.findLatestCompletedByProject(projectId); }
    @Override public java.util.List<NovelCoverUploadSession> findExpiredPending(java.time.Instant now) { return mapper.findExpiredPending(now); }
    @Override public NovelProject lockProject(Long projectId) { return mapper.lockProject(projectId); }
    @Override public int setPendingUpload(Long projectId, Long ownerUserId, Long uploadId) { return mapper.setPendingUpload(projectId, ownerUserId, uploadId); }
    @Override public int markProcessing(Long uploadId, Double cropX, Double cropY, Double cropWidth, Double cropHeight,
                                        Integer imageWidth, Integer imageHeight, String displayObjectKey, String thumbnailObjectKey) {
        return mapper.markProcessing(uploadId, cropX, cropY, cropWidth, cropHeight, imageWidth, imageHeight,
                displayObjectKey, thumbnailObjectKey);
    }
    @Override public int retry(Long uploadId) { return mapper.retry(uploadId); }
    @Override public int markCompleted(Long uploadId) { return mapper.markCompleted(uploadId); }
    @Override public int markFailed(Long uploadId, String errorCode, String errorMessage) { return mapper.markFailed(uploadId, errorCode, errorMessage); }
    @Override public int markSuperseded(Long uploadId) { return mapper.markSuperseded(uploadId); }
    @Override public int markExpired(Long uploadId) { return mapper.markExpired(uploadId); }
    @Override public int applyCover(Long projectId, Long uploadId, String originalObjectKey, String displayObjectKey, String thumbnailObjectKey) {
        return mapper.applyCover(projectId, uploadId, originalObjectKey, displayObjectKey, thumbnailObjectKey);
    }
    @Override public int clearCover(Long projectId, Long ownerUserId) { return mapper.clearCover(projectId, ownerUserId); }
}
