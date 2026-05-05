package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 实时事件发布服务实现。
 * <p>统一向项目级 WebSocket 通道与任务级 SSE 通道投递生成相关事件，保证不同客户端订阅方式都能收到同一业务事件。</p>
 */
@Service
public class RealtimeEventServiceImpl implements RealtimeEventService {

    private final ProjectWebSocketSessionRegistry sessionRegistry;
    private final GenerationSseEmitterHub generationSseEmitterHub;
    private final ObjectMapper objectMapper;

    public RealtimeEventServiceImpl(ProjectWebSocketSessionRegistry sessionRegistry,
                                    GenerationSseEmitterHub generationSseEmitterHub,
                                    ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.generationSseEmitterHub = generationSseEmitterHub;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布项目级业务事件。
     * <p>流程：构建标准事件信封（event/projectId/timestamp/data）-> 广播到项目 WebSocket 会话。</p>
     */
    @Override
    public void publishProjectEvent(Long projectId, String eventType, Object data) {
        if (projectId == null || eventType == null || eventType.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventType);
        payload.put("projectId", projectId);
        payload.put("timestamp", Instant.now().toString());
        payload.put("data", data);
        broadcast(projectId, payload);
    }

    /**
     * 发布“生成已开始”事件。
     * <p>流程：先投递项目级事件，再投递任务 SSE 事件，确保列表页与详情页同步感知任务启动。</p>
     */
    @Override
    public void publishGenerationStarted(Long projectId, Long taskId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", "running");
        publishProjectEvent(projectId, "generation.started", payload);
        generationSseEmitterHub.publish(taskId, "generation.started", payload);
    }

    /**
     * 发布流式 token 事件。
     * <p>流程：构建 token 载荷 -> 同步投递到 WebSocket 与 SSE；若 done=true 则追加发布完成事件。</p>
     */
    @Override
    public void publishGenerationToken(Long projectId, Long taskId, String token, boolean done) {
        Map<String, Object> tokenPayload = new LinkedHashMap<>();
        tokenPayload.put("taskId", taskId);
        tokenPayload.put("token", token);
        tokenPayload.put("done", done);

        // 同一 token 事件同时投递到 WebSocket 与 SSE，兼容不同客户端订阅通道。
        publishProjectEvent(projectId, "generation.token", tokenPayload);
        generationSseEmitterHub.publish(taskId, "generation.token", tokenPayload);
        if (done) {
            publishGenerationDone(projectId, taskId, "done");
        }
    }

    /**
     * 发布工具调用过程事件。
     * <p>业务意义：向前端实时暴露插件/工具执行状态，便于展示“调用中/成功/失败”过程。</p>
     */
    @Override
    public void publishGenerationToolCall(Long projectId,
                                          Long taskId,
                                          String pluginCode,
                                          String toolName,
                                          String status,
                                          String errorMsg,
                                          String output) {
        publishGenerationToolCall(projectId, taskId, null, pluginCode, toolName, status, null, null, null, null, errorMsg, output);
    }

    @Override
    public void publishGenerationToolCall(Long projectId,
                                          Long taskId,
                                          String toolCallId,
                                          String pluginCode,
                                          String toolName,
                                          String status,
                                          Long approvalId,
                                          String approvalType,
                                          Integer iteration,
                                          Object argumentsPreview,
                                          String errorMsg,
                                          String output) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        if (toolCallId != null && !toolCallId.isBlank()) {
            payload.put("toolCallId", toolCallId);
        }
        payload.put("pluginCode", pluginCode);
        payload.put("toolCode", pluginCode);
        payload.put("toolName", toolName);
        payload.put("status", status);
        if (approvalId != null) {
            payload.put("approvalId", approvalId);
        }
        if (approvalType != null && !approvalType.isBlank()) {
            payload.put("approvalType", approvalType);
        }
        if (iteration != null) {
            payload.put("iteration", iteration);
        }
        if (argumentsPreview != null) {
            payload.put("argumentsPreview", argumentsPreview);
        }
        payload.put("errorMsg", errorMsg == null ? "" : errorMsg);
        payload.put("output", output == null ? "" : output);
        publishProjectEvent(projectId, "generation.tool_call", payload);
        generationSseEmitterHub.publish(taskId, "generation.tool_call", payload);
    }

    /**
     * 发布“等待审批”事件。
     * <p>业务意义：告知前端当前生成任务被审批节点阻塞，需要人工介入。</p>
     */
    @Override
    public void publishGenerationWaitingApproval(Long projectId, Long taskId, Long approvalId, String approvalType) {
        publishGenerationWaitingApproval(projectId, taskId, null, approvalId, approvalType, null, null, null);
    }

    @Override
    public void publishGenerationWaitingApproval(Long projectId,
                                                 Long taskId,
                                                 String toolCallId,
                                                 Long approvalId,
                                                 String approvalType,
                                                 Object approvalPreview,
                                                 String resumeMode,
                                                 ToolApprovalView approvalView) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        if (toolCallId != null && !toolCallId.isBlank()) {
            payload.put("toolCallId", toolCallId);
        }
        payload.put("approvalId", approvalId);
        payload.put("approvalType", approvalType);
        if (approvalView != null) {
            if (approvalView.toolCode() != null && !approvalView.toolCode().isBlank()) {
                payload.put("toolCode", approvalView.toolCode());
            }
            if (approvalView.toolDisplayName() != null && !approvalView.toolDisplayName().isBlank()) {
                payload.put("toolDisplayName", approvalView.toolDisplayName());
            }
            if (approvalView.riskLevel() != null) {
                payload.put("riskLevel", approvalView.riskLevel());
            }
            if (approvalView.operationCode() != null && !approvalView.operationCode().isBlank()) {
                payload.put("operationCode", approvalView.operationCode());
            }
        }
        if (approvalPreview != null) {
            payload.put("approvalPreview", approvalPreview);
        }
        if (resumeMode != null && !resumeMode.isBlank()) {
            payload.put("resumeMode", resumeMode);
        }
        payload.put("status", "waiting_approval");
        publishProjectEvent(projectId, "generation.waiting_approval", payload);
        generationSseEmitterHub.publish(taskId, "generation.waiting_approval", payload);
    }

    /**
     * 发布“生成完成”事件并关闭 SSE 通道。
     */
    @Override
    public void publishGenerationDone(Long projectId, Long taskId, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", status);
        publishProjectEvent(projectId, "generation.done", payload);
        generationSseEmitterHub.publish(taskId, "generation.done", payload);
        generationSseEmitterHub.complete(taskId);
    }

    /**
     * 发布“生成失败”事件并关闭 SSE 通道。
     */
    @Override
    public void publishGenerationFailed(Long projectId, Long taskId, String errorCode, String errorMsg) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", "failed");
        payload.put("errorCode", errorCode);
        payload.put("errorMsg", errorMsg == null ? "" : errorMsg);
        publishProjectEvent(projectId, "generation.failed", payload);
        generationSseEmitterHub.publish(taskId, "generation.failed", payload);
        generationSseEmitterHub.complete(taskId);
    }

    /**
     * 广播 WebSocket 消息到项目所有在线会话。
     * <p>流程：清理失效连接 -> 序列化 payload -> 安全发送并剔除发送失败会话。</p>
     */
    private void broadcast(Long projectId, Map<String, Object> payload) {
        // 广播前先清理失效连接，避免发送异常影响正常会话。
        sessionRegistry.cleanupClosed(projectId);
        Set<WebSocketSession> sessions = sessionRegistry.sessions(projectId);
        if (sessions.isEmpty()) {
            return;
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return;
        }
        sessions.removeIf(session -> !safeSend(session, text));
    }

    /**
     * 安全发送单条 WebSocket 文本消息。
     *
     * @return true=发送成功；false=会话不可用或发送失败
     */
    private boolean safeSend(WebSocketSession session, String text) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(text));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}

