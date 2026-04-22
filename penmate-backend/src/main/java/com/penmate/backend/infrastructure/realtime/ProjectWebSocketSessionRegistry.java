package com.penmate.backend.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目 WebSocket 会话注册中心。
 * <p>按项目维度维护在线 WebSocket 会话集合，供实时事件广播、连接清理与连接强制回收使用。</p>
 */
@Component
public class ProjectWebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByProject = new ConcurrentHashMap<>();

    /**
     * 注册项目会话。
     * <p>流程：按项目ID获取并初始化会话集合，再将当前会话加入集合。</p>
     */
    public void register(Long projectId, WebSocketSession session) {
        sessionsByProject.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /**
     * 反注册项目会话。
     * <p>流程：移除指定会话；若项目无剩余会话则回收该项目键。</p>
     */
    public void unregister(Long projectId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByProject.get(projectId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByProject.remove(projectId);
        }
    }

    /**
     * 获取项目当前会话集合。
     * <p>若项目暂无连接，返回空集合。</p>
     */
    public Set<WebSocketSession> sessions(Long projectId) {
        return sessionsByProject.getOrDefault(projectId, Set.of());
    }

    /**
     * 清理项目下已关闭会话。
     * <p>流程：移除非 open 状态连接；若清理后为空则移除项目键。</p>
     */
    public void cleanupClosed(Long projectId) {
        Set<WebSocketSession> sessions = sessionsByProject.get(projectId);
        if (sessions == null) {
            return;
        }
        sessions.removeIf(session -> !session.isOpen());
        if (sessions.isEmpty()) {
            sessionsByProject.remove(projectId);
        }
    }

    /**
     * 强制关闭项目下所有会话。
     * <p>用于项目下线或异常回收场景，逐个关闭连接后清空注册数据。</p>
     */
    public void forceCloseAll(Long projectId) {
        Set<WebSocketSession> sessions = sessionsByProject.get(projectId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                session.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
        sessionsByProject.remove(projectId);
    }
}

