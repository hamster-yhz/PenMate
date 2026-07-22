package com.penmate.backend.application.novel;

import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ChapterAiUndoRetentionService {

    private final NovelGateway novelGateway;

    public ChapterAiUndoRetentionService(NovelGateway novelGateway) {
        this.novelGateway = novelGateway;
    }

    public int deleteExpired() {
        return novelGateway.deleteExpiredAiUndo(Instant.now());
    }
}
