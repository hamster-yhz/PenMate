package com.penmate.backend.application.iam;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class AuthorizationChangeDispatcher {
    public static final String REVOKE_SESSIONS_JOB = "IAM_REVOKE_AUTH_SESSIONS";

    private final AsyncJobQueueService jobs;
    private final JsonCodec json;

    public AuthorizationChangeDispatcher(AsyncJobQueueService jobs, JsonCodec json) {
        this.jobs = jobs;
        this.json = json;
    }

    public void revokeSessionsAfterCommit(List<Long> userIds, String changeKey, Long actorUserId) {
        List<Long> uniqueUserIds = List.copyOf(new LinkedHashSet<>(userIds == null ? List.of() : userIds));
        if (uniqueUserIds.isEmpty()) return;
        jobs.enqueue(REVOKE_SESSIONS_JOB, "iam:authz:" + changeKey, actorUserId, null,
                json.write(Map.of("userIds", uniqueUserIds)));
    }
}
