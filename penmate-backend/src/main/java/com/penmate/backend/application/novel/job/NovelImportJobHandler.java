package com.penmate.backend.application.novel.job;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.novel.importing.NovelImportMaterializationService;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.domain.novel.importing.NovelImportSession;
import com.penmate.backend.domain.novel.repository.NovelImportSessionRepository;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NovelImportJobHandler implements AsyncJobHandler {
    private static final int BATCH_SIZE = 25;
    private final JsonCodec json;
    private final NovelImportSessionRepository sessions;
    private final NovelImportMaterializationService materialization;

    public NovelImportJobHandler(JsonCodec json, NovelImportSessionRepository sessions,
                                 NovelImportMaterializationService materialization) {
        this.json = json;
        this.sessions = sessions;
        this.materialization = materialization;
    }

    @Override public String jobType() { return "NOVEL_IMPORT"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        Map<String, Object> payload = json.readObject(job.getPayloadJson());
        Long sessionId = Long.valueOf(String.valueOf(payload.get("sessionId")));
        try {
            NovelImportSession session = materialization.prepare(sessionId);
            int total = session.getTotalChapters();
            int current = session.getCheckpointChapter();
            context.heartbeat(current, total, "正在导入章节");
            while (current < total) {
                current = materialization.appendNextBatch(sessionId, BATCH_SIZE);
                context.heartbeat(current, total, "已导入 %d / %d 章".formatted(current, total));
            }
            Long projectId = materialization.publish(sessionId);
            context.heartbeat(total, total, "作品已创建");
            return json.write(Map.of("sessionId", sessionId, "projectId", projectId, "status", "COMPLETED"));
        } catch (AsyncJobExecutionContext.JobCancelledException cancelled) {
            NovelImportSession session = sessions.findById(sessionId);
            if (session == null || !"PAUSED".equals(session.getStatus())) {
                materialization.cancelAndCleanup(sessionId, false, null);
            }
            throw cancelled;
        } catch (RuntimeException exception) {
            if (job.getAttemptCount() != null && job.getMaxAttempts() != null
                    && job.getAttemptCount() >= job.getMaxAttempts()) {
                materialization.cancelAndCleanup(sessionId, true, exception.getMessage());
            }
            throw exception;
        }
    }
}
