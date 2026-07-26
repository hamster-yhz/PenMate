package com.penmate.backend.application.iam;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IamRevokeAuthSessionsJobHandler implements AsyncJobHandler {
    private final JsonCodec json;
    private final AuthorizationSessionRevocationService revocations;

    public IamRevokeAuthSessionsJobHandler(JsonCodec json,
                                           AuthorizationSessionRevocationService revocations) {
        this.json = json;
        this.revocations = revocations;
    }

    @Override
    public String jobType() {
        return AuthorizationChangeDispatcher.REVOKE_SESSIONS_JOB;
    }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        Map<String, Object> payload = json.readObject(job.getPayloadJson());
        Object rawIds = payload.get("userIds");
        if (!(rawIds instanceof List<?> values)) throw new IllegalArgumentException("userIds are required");
        List<Long> userIds = values.stream().map(value -> Long.parseLong(String.valueOf(value))).toList();
        revocations.revokeAll(userIds);
        context.heartbeat(userIds.size(), userIds.size(), "Authorization sessions revoked");
        return json.write(Map.of("revokedUserCount", userIds.size()));
    }
}
