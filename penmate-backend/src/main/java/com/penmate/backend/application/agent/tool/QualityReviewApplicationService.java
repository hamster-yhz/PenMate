package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;

/**
 * 质量审查应用服务，负责承载 quality_review 的业务流程。
 */
public interface QualityReviewApplicationService {

    ToolCallResult review(ToolCallRequest request);
}
