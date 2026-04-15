package com.penmate.backend.interfaces.api.approval;

import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.interfaces.api.approval.dto.CreateApprovalRequestDto;
import com.penmate.backend.interfaces.api.approval.dto.ReviewApprovalRequestDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ApprovalController。
 * <p>控制层：负责HTTP请求接入、参数校验与统一响应封装。</p>
 */
@RestController
@RequestMapping("/api/v1/novels/{projectId}/approvals")
public class ApprovalController {

    private final ApprovalApplicationService approvalApplicationService;

    public ApprovalController(ApprovalApplicationService approvalApplicationService) {
        this.approvalApplicationService = approvalApplicationService;
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping
    public ApiResponse<ApprovalRequest> create(@PathVariable Long projectId,
                                               @Valid @RequestBody CreateApprovalRequestDto dto,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        CreateApprovalCommand command = new CreateApprovalCommand(
                projectId,
                dto.getTaskId(),
                dto.getApprovalType(),
                dto.getPayloadJson(),
                dto.getRiskLevel(),
                dto.getRequestedBy()
        );
        ApprovalRequest created = approvalApplicationService.create(command, traceId);
        return ApiResponse.success(created, traceId);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @param status 入参：status
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping
    public ApiResponse<List<ApprovalRequest>> list(@PathVariable Long projectId,
                                                   @RequestParam(value = "status", required = false) String status,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<ApprovalRequest> items = approvalApplicationService.listByProject(projectId);
        if (status != null && !status.isBlank()) {
            items = items.stream().filter(it -> status.equalsIgnoreCase(it.getStatus())).toList();
        }
        return ApiResponse.success(items, traceId);
    }

    /**
     * 处理业务请求。
     *
     * @param approvalId 入参：approvalId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{approvalId}")
    public ApiResponse<ApprovalRequest> detail(@PathVariable Long approvalId,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(approvalApplicationService.detail(approvalId), traceId);
    }

    /**
     * 处理业务请求。
     *
     * @param approvalId 入参：approvalId
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{approvalId}/approve")
    public ApiResponse<String> approve(@PathVariable Long approvalId,
                                       @Valid @RequestBody ReviewApprovalRequestDto dto,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ReviewApprovalCommand command = new ReviewApprovalCommand(dto.getReviewedBy(), dto.getComment());
        approvalApplicationService.approve(approvalId, command, traceId);
        return ApiResponse.success("approved", traceId);
    }

    /**
     * 处理业务请求。
     *
     * @param approvalId 入参：approvalId
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{approvalId}/reject")
    public ApiResponse<String> reject(@PathVariable Long approvalId,
                                      @Valid @RequestBody ReviewApprovalRequestDto dto,
                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ReviewApprovalCommand command = new ReviewApprovalCommand(dto.getReviewedBy(), dto.getComment());
        approvalApplicationService.reject(approvalId, command, traceId);
        return ApiResponse.success("rejected", traceId);
    }
}

