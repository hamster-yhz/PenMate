package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.QualityReviewApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Quality review tool 处理器。
 */
@Component
@Slf4j
public class QualityReviewToolHandler implements AgentToolHandler {

    private final QualityReviewApplicationService qualityReviewApplicationService;

    public QualityReviewToolHandler(QualityReviewApplicationService qualityReviewApplicationService) {
        this.qualityReviewApplicationService = qualityReviewApplicationService;
    }

    @Override
    public String toolCode() {
        return "quality_review";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("request must not be null");
            }
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "quality review execution failed"
                    : ex.getMessage();
            log.warn("quality_review 参数非法: runId={}, traceId={}, message={}",
                    request == null ? null : request.runId(),
                    request == null ? null : request.traceId(),
                    message);
            return new ToolCallResult("FAILED", null, null, "QUALITY_REVIEW_FAILED", message);
        }

        try {
            return qualityReviewApplicationService.review(request);
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "quality review execution failed"
                    : ex.getMessage();
            log.warn("quality_review 执行失败: projectId={}, runId={}, traceId={}, message={}",
                    request.projectId(), request.runId(), request.traceId(), errorMessage);
            return new ToolCallResult("FAILED", null, null, "QUALITY_REVIEW_FAILED", errorMessage);
        }
    }
}
