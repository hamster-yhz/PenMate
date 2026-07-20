package com.penmate.backend.application.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;

public interface AsyncJobHandler {
    String jobType();
    String execute(OpsAsyncJob job, AsyncJobExecutionContext context) throws Exception;
}
