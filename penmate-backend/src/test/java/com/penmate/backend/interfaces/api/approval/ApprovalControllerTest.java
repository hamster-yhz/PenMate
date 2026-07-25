package com.penmate.backend.interfaces.api.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    private ApprovalApplicationService approvalApplicationService;

    @InjectMocks
    private ApprovalController approvalController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(approvalController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    // 创建审批单成功�?
    void UT_APPROVAL_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-CREATE-SUCCESS";
        ApprovalRequest created = new ApprovalRequest();
        created.setApprovalRequestId(88001L);
        created.setProjectId(10001L);
        created.setRequestedBy(1001L);
        created.setStatus("pending");

        when(approvalApplicationService.create(any(), eq(traceId))).thenReturn(created);

        mockMvc().perform(post("/api/v1/novels/10001/approvals")
                        .principal(principal("1001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "10001",
                                "taskId", "7001",
                                "approvalType", "create_card",
                                "payloadJson", "{\"name\":\"A\"}",
                                "riskLevel", 2,
                                "requestedBy", "1001"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("88001"))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 创建审批单参数错误�?
    void UT_APPROVAL_CREATE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-CREATE-INVALID";

        mockMvc().perform(post("/api/v1/novels/10001/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", "10001",
                                "payloadJson", "{}",
                                "riskLevel", 2,
                                "requestedBy", "1001"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 列表按状态过滤�?
    void UT_APPROVAL_LIST_FILTER_STATUS() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-LIST-FILTER";

        ApprovalRequest pending = new ApprovalRequest();
        pending.setApprovalRequestId(88001L);
        pending.setStatus("pending");
        ApprovalRequest approved = new ApprovalRequest();
        approved.setApprovalRequestId(88002L);
        approved.setStatus("approved");

        when(approvalApplicationService.listByProject(10001L, 2001L)).thenReturn(List.of(pending, approved));

        mockMvc().perform(get("/api/v1/novels/10001/approvals")
                        .principal(principal("2001"))
                        .param("status", "approved")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("88002"))
                .andExpect(jsonPath("$.data[0].status").value("approved"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 重复审批被拦截�?
    void UT_APPROVAL_REVIEW_REPEAT_BLOCKED() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-REVIEW-BLOCKED";
        doThrow(new IllegalArgumentException("Approval is not in pending status or not found"))
                .when(approvalApplicationService).approve(eq(10001L), eq(88001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/approvals/88001/approve")
                        .principal(principal("2001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewedBy", "2001",
                                "comment", "repeat review"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 审批详情不存在�?
    void UT_APPROVAL_DETAIL_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-DETAIL-NOT-FOUND";
        doThrow(new IllegalArgumentException("Approval not found"))
                .when(approvalApplicationService).detail(10001L, 99999L, 2001L);

        mockMvc().perform(get("/api/v1/novels/10001/approvals/99999")
                        .principal(principal("2001"))
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("Approval not found"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 审批通过成功�?
    void UT_APPROVAL_APPROVE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-APPROVE-SUCCESS";
        doNothing().when(approvalApplicationService).approve(eq(10001L), eq(88001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/approvals/88001/approve")
                        .principal(principal("2001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewedBy", "2001",
                                "comment", "approved"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("approved"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 审批拒绝成功�?
    void UT_APPROVAL_REJECT_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-REJECT-SUCCESS";
        doNothing().when(approvalApplicationService).reject(eq(10001L), eq(88001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/approvals/88001/reject")
                        .principal(principal("2001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewedBy", "2001",
                                "comment", "rejected"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("rejected"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }
    @Test
    void UT_APPROVAL_REJECTS_LEGACY_PREFIX_IDS() throws Exception {
        String traceId = "UT-TRACE-APPROVAL-LEGACY-ID-REJECT";

        mockMvc().perform(get("/api/v1/novels/project-10001/approvals/approval-88001")
                        .principal(principal("2001"))
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    private Principal principal(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}


