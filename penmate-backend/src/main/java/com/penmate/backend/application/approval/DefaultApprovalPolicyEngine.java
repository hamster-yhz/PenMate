package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.ToolInvocationRequest;
import com.penmate.backend.application.agent.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认审批策略引擎。
 * <p>
 * 该组件负责把 tool 静态元数据与本次调用参数结合起来，得出“是否需要审批”的运行时结论。
 * 它既支持基于元数据的固定审批规则，也支持基于参数内容的轻量风险识别。
 * 当前对 {@code book_crud} 删除操作的识别仍依赖原始 JSON 文本包含判断，
 * 属于实现约束明确的临时方案，而不是健壮的结构化语义解析。
 * </p>
 */
@Component
@Slf4j
public class DefaultApprovalPolicyEngine {

    /**
     * 评估一次 tool 调用的审批要求。
     *
     * @param metadata tool 静态元数据
     * @param request 本次 tool 调用请求
     * @return 审批决策结果
     */
    public ApprovalPolicyDecision evaluate(ToolMetadata metadata, ToolInvocationRequest request) {
        if (metadata == null || request == null) {
            throw new IllegalArgumentException("metadata and request must not be null");
        }
        log.debug("开始评估审批策略: toolCode={}, traceId={}", metadata.toolCode(), request.traceId());
        // 同一个 tool 可能包含多种操作；当前实现对 book_crud 的 delete 采用原始 JSON 文本匹配，
        // 只有精确包含 "operation":"delete" 时才会升级为人工审批。
        if ("book_crud".equals(metadata.toolCode()) && containsDeleteOperation(request.toolArgsJson())) {
            log.info("审批策略命中高风险删除: toolCode={}, approvalType=BOOK_DELETE, traceId={}", metadata.toolCode(), request.traceId());
            return new ApprovalPolicyDecision(true, "BOOK_DELETE");
        }
        // 若未命中参数级高风险规则，则退回到 tool 元数据上的通用审批声明。
        if (metadata.approvalRequired()) {
            String approvalType = metadata.approvalType() == null ? "" : metadata.approvalType();
            log.info("审批策略命中元数据审批声明: toolCode={}, approvalType={}, traceId={}", metadata.toolCode(), approvalType, request.traceId());
            return new ApprovalPolicyDecision(true, approvalType);
        }
        log.debug("审批策略判定直通: toolCode={}, traceId={}", metadata.toolCode(), request.traceId());
        return new ApprovalPolicyDecision(false, "");
    }

    /**
     * 判断参数文本中是否包含 delete 操作片段。
     * <p>
     * 当前实现没有把 JSON 结构化解析为 operation 再判定，而是直接检查原始文本中是否包含
     * {@code "operation":"delete"}。因此它依赖调用方的 JSON 序列化格式，
     * 例如空格、换行或其他格式差异都可能影响命中结果。
     * </p>
     *
     * @param toolArgsJson tool 参数 JSON
     * @return true 表示按删除风险处理
     */
    private boolean containsDeleteOperation(String toolArgsJson) {
        if (toolArgsJson == null) {
            return false;
        }
        return toolArgsJson.contains("\"operation\":\"delete\"");
    }
}
