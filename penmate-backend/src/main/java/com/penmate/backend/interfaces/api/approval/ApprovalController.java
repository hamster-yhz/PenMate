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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 审批请求控制器�?
 * <p>负责审批单创建、列表查询、详情查询与审核动作（通过/拒绝）的 HTTP 接入，并将请求转换为审批应用层命令�?/p>
 */
@RestController
@RequestMapping("/api/v1/novels/{projectId}/approvals")
public class ApprovalController {

    private final ApprovalApplicationService approvalApplicationService;

    public ApprovalController(ApprovalApplicationService approvalApplicationService) {
        this.approvalApplicationService = approvalApplicationService;
    }

    /**
     * 创建审批请求�?
     * <p><b>业务目的�?/b>将高风险或需人工确认的业务变更提交为审批单，进入后续审核流程�?/p>
     * <p><b>流程主线�?/b>校验请求�?-> 组装 {@link CreateApprovalCommand} -> 调用应用服务创建审批�?-> 返回新审批对象�?/p>
     * <p><b>关键调用�?/b>{@code approvalApplicationService.create(command, traceId)} 负责审批单落库与状态初始化�?/p>
     * <p><b>异常与分支：</b>任务不存在、审批类型非法或请求人无权限时返回业务异常�?/p>
     * <p><b>副作用：</b>新增审批请求记录�?/p>
     * <p><b>ID semantics:</b> projectId / runId / requestedBy are business IDs.</p>
     *
     * @param projectId 入参：projectId（项目业务ID�?
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结�?
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@PathVariable String projectId,
                                                   @Valid @RequestBody CreateApprovalRequestDto dto,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        CreateApprovalCommand command = new CreateApprovalCommand(
                requireLongId(projectId, "projectId"),
                optionalLongId(dto.getRunId(), "runId"),
                dto.getApprovalType(),
                dto.getPayloadJson(),
                dto.getRiskLevel(),
                requireLongId(dto.getRequestedBy(), "requestedBy")
        );
        ApprovalRequest created = approvalApplicationService.create(command, traceId);
        return ApiResponse.success(toApprovalView(created), traceId);
    }

    /**
     * 查询项目审批列表�?
     * <p><b>业务目的�?/b>返回项目下审批请求集合，并支持按状态筛选，供审批中心展示�?/p>
     * <p><b>流程主线�?/b>查询项目审批列表 -> 按可选状态参数过�?-> 统一封装响应�?/p>
     * <p><b>关键调用�?/b>{@code approvalApplicationService.listByProject(projectId)} 获取基础集合�?/p>
     * <p><b>异常与分支：</b>状态参数为空时返回全量；非空时执行大小写无关过滤�?/p>
     * <p><b>副作用：</b>无持久化写入�?/p>
     * <p><b>ID 语义�?/b>projectId 为项目业�?ID�?/p>
     *
     * @param projectId 入参：projectId（项目业务ID�?
     * @param status 入参：status
     * @param traceId 入参：traceId
     * @return 出参：处理结�?
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable String projectId,
                                                       @RequestParam(value = "status", required = false) String status,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<ApprovalRequest> items = approvalApplicationService.listByProject(requireLongId(projectId, "projectId"));
        if (status != null && !status.isBlank()) {
            items = items.stream().filter(it -> status.equalsIgnoreCase(it.getStatus())).toList();
        }
        return ApiResponse.success(items.stream().map(this::toApprovalView).toList(), traceId);
    }

    /**
     * 查询审批详情�?
     * <p><b>业务目的�?/b>返回单个审批请求的完整信息，支持审批页展示上下文与历史状态�?/p>
     * <p><b>流程主线�?/b>接收审批业务ID -> 调用应用服务查询详情 -> 封装统一响应�?/p>
     * <p><b>关键调用�?/b>{@code approvalApplicationService.detail(approvalId)}�?/p>
     * <p><b>异常与分支：</b>审批单不存在时返回业务异常�?/p>
     * <p><b>副作用：</b>无持久化写入�?/p>
     * <p><b>ID 语义�?/b>approvalId 为审批单业务 ID�?/p>
     *
     * @param approvalId 入参：approvalId（审批单业务ID�?
     * @param traceId 入参：traceId
     * @return 出参：处理结�?
     */
    @GetMapping("/{approvalId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String approvalId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toApprovalView(approvalApplicationService.detail(requireLongId(approvalId, "approvalId"))), traceId);
    }

    /**
     * 审批通过�?
     * <p><b>业务目的�?/b>将审批单状态推进为通过，并触发后续业务落地链路�?/p>
     * <p><b>流程主线�?/b>校验审核入参 -> 组装 {@link ReviewApprovalCommand} -> 调用应用服务执行通过动作 -> 返回确认结果�?/p>
     * <p><b>关键调用�?/b>{@code approvalApplicationService.approve(approvalId, command, traceId)}�?/p>
     * <p><b>异常与分支：</b>审批单状态不可变更、审核人无权限时返回业务异常�?/p>
     * <p><b>副作用：</b>更新审批状态并记录审核意见�?/p>
     * <p><b>ID 语义�?/b>approvalId / reviewedBy 均为业务语义 ID�?/p>
     *
     * @param approvalId 入参：approvalId（审批单业务ID�?
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结�?
     */
    @PostMapping("/{approvalId}/approve")
    public ApiResponse<String> approve(@PathVariable String approvalId,
                                       @Valid @RequestBody ReviewApprovalRequestDto dto,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ReviewApprovalCommand command = new ReviewApprovalCommand(requireLongId(dto.getReviewedBy(), "reviewedBy"), dto.getComment());
        approvalApplicationService.approve(requireLongId(approvalId, "approvalId"), command, traceId);
        return ApiResponse.success("approved", traceId);
    }

    /**
     * 审批拒绝�?
     * <p><b>业务目的�?/b>拒绝当前审批请求并阻断后续业务执行�?/p>
     * <p><b>流程主线�?/b>校验审核入参 -> 组装 {@link ReviewApprovalCommand} -> 调用应用服务执行拒绝动作 -> 返回确认结果�?/p>
     * <p><b>关键调用�?/b>{@code approvalApplicationService.reject(approvalId, command, traceId)}�?/p>
     * <p><b>异常与分支：</b>审批状态不允许拒绝或审核人权限不足时返回业务异常�?/p>
     * <p><b>副作用：</b>更新审批状态并记录拒绝意见�?/p>
     * <p><b>ID 语义�?/b>approvalId / reviewedBy 均为业务语义 ID�?/p>
     *
     * @param approvalId 入参：approvalId（审批单业务ID�?
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结�?
     */
    @PostMapping("/{approvalId}/reject")
    public ApiResponse<String> reject(@PathVariable String approvalId,
                                      @Valid @RequestBody ReviewApprovalRequestDto dto,
                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ReviewApprovalCommand command = new ReviewApprovalCommand(requireLongId(dto.getReviewedBy(), "reviewedBy"), dto.getComment());
        approvalApplicationService.reject(requireLongId(approvalId, "approvalId"), command, traceId);
        return ApiResponse.success("rejected", traceId);
    }

    private Map<String, Object> toApprovalView(ApprovalRequest approvalRequest) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", approvalRequest == null ? null : stringifyBusinessId(approvalRequest.getApprovalRequestId()));
        data.put("projectId", approvalRequest == null ? null : stringifyBusinessId(approvalRequest.getProjectId()));
        data.put("runId", approvalRequest == null ? null : stringifyBusinessId(approvalRequest.getRunId()));
        data.put("approvalType", approvalRequest == null ? null : approvalRequest.getApprovalType());
        data.put("payloadJson", approvalRequest == null ? null : approvalRequest.getPayloadJson());
        data.put("riskLevel", approvalRequest == null ? null : approvalRequest.getRiskLevel());
        data.put("status", approvalRequest == null ? null : approvalRequest.getStatus());
        data.put("requestedBy", approvalRequest == null ? null : stringifyBusinessId(approvalRequest.getRequestedBy()));
        data.put("reviewedBy", approvalRequest == null ? null : stringifyBusinessId(approvalRequest.getReviewedBy()));
        data.put("reviewedAt", approvalRequest == null ? null : approvalRequest.getReviewedAt());
        data.put("reviewComment", approvalRequest == null ? null : approvalRequest.getReviewComment());
        data.put("createdAt", approvalRequest == null ? null : approvalRequest.getCreatedAt());
        data.put("updatedAt", approvalRequest == null ? null : approvalRequest.getUpdatedAt());
        return data;
    }

    private Long requireLongId(String rawValue, String fieldName) {
        String normalized = Objects.requireNonNull(rawValue, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!normalized.matches("^\\d+$")) {
            throw new IllegalArgumentException(fieldName + " must be a numeric string business id");
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid numeric string business id", ex);
        }
    }

    private Long optionalLongId(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return requireLongId(rawValue, fieldName);
    }

    private String stringifyBusinessId(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}



