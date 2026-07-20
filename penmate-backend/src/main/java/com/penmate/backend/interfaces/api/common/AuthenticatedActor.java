package com.penmate.backend.interfaces.api.common;

import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.security.core.Authentication;

public final class AuthenticatedActor {
    private AuthenticatedActor() { }

    public static Long id(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw BusinessException.unauthorized("Login required");
        }
        String value = authentication.getName();
        if (value == null || !value.matches("\\d+")) throw BusinessException.unauthorized("Invalid authenticated user");
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw BusinessException.unauthorized("Invalid authenticated user"); }
    }
}
