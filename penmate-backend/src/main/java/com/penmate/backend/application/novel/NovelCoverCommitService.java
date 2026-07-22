package com.penmate.backend.application.novel;

import com.penmate.backend.domain.novel.model.NovelCoverUploadSession;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelCoverUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Service
public class NovelCoverCommitService {
    private final NovelCoverUploadRepository uploads;

    public NovelCoverCommitService(NovelCoverUploadRepository uploads) {
        this.uploads = uploads;
    }

    @Transactional
    public CommitResult commit(Long uploadId) {
        NovelCoverUploadSession session = uploads.findByIdForUpdate(uploadId);
        if (session == null || !"PROCESSING".equals(session.getStatus())) return CommitResult.supersededResult();
        NovelProject project = uploads.lockProject(session.getProjectId());
        if (project == null || !uploadId.equals(project.getCoverPendingUploadId())) {
            uploads.markSuperseded(uploadId);
            return CommitResult.supersededResult();
        }
        List<String> replaced = Stream.of(project.getCoverOriginalObjectKey(), project.getCoverDisplayObjectKey(),
                        project.getCoverThumbnailObjectKey())
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !value.equals(session.getOriginalObjectKey()))
                .toList();
        if (uploads.applyCover(project.getProjectId(), uploadId, session.getOriginalObjectKey(),
                session.getDisplayObjectKey(), session.getThumbnailObjectKey()) != 1) {
            uploads.markSuperseded(uploadId);
            return CommitResult.supersededResult();
        }
        uploads.markCompleted(uploadId);
        return new CommitResult(true, replaced);
    }

    public record CommitResult(boolean applied, List<String> replacedObjectKeys) {
        static CommitResult supersededResult() { return new CommitResult(false, List.of()); }
    }
}
