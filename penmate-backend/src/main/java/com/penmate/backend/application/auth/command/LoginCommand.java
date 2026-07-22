package com.penmate.backend.application.auth.command;

/**
 * LoginCommand。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
public record LoginCommand(
        String email,
        String password,
        String userAgent,
        String ipAddress
) {
    public LoginCommand(String email, String password) {
        this(email, password, "", "unknown");
    }
}

