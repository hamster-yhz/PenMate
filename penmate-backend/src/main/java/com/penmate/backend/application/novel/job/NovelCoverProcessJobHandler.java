package com.penmate.backend.application.novel.job;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.novel.NovelCoverApplicationService;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NovelCoverProcessJobHandler implements AsyncJobHandler {
    private final JsonCodec jsonCodec;
    private final NovelCoverApplicationService covers;

    public NovelCoverProcessJobHandler(JsonCodec jsonCodec, NovelCoverApplicationService covers) {
        this.jsonCodec = jsonCodec;
        this.covers = covers;
    }

    @Override public String jobType() { return "NOVEL_COVER_PROCESS"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        Map<String, Object> payload = jsonCodec.readObject(job.getPayloadJson());
        long uploadId = Long.parseLong(String.valueOf(payload.get("uploadId")));
        try {
            covers.process(uploadId);
            context.heartbeat(1, 1, "Cover processed");
            return jsonCodec.write(Map.of("uploadId", uploadId, "status", "COMPLETED"));
        } catch (RuntimeException exception) {
            if (job.getAttemptCount() != null && job.getMaxAttempts() != null
                    && job.getAttemptCount() >= job.getMaxAttempts()) {
                covers.markFailed(uploadId, exception);
            }
            throw exception;
        }
    }
}
