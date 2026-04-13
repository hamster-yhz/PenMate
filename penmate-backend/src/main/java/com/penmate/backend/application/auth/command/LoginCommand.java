package com.penmate.backend.application.auth.command;

public record LoginCommand(
        String email,
        String password
) {
}

