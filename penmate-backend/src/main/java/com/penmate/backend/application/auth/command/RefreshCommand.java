package com.penmate.backend.application.auth.command;

/**
 * RefreshCommand。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
public record RefreshCommand(
        String refreshToken,
        String ipAddress
) {
    public RefreshCommand(String refreshToken) {
        this(refreshToken, "unknown");
    }
}

