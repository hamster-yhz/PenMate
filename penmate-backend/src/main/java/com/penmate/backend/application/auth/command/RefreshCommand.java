package com.penmate.backend.application.auth.command;

public record RefreshCommand(
        String refreshToken
) {
}

