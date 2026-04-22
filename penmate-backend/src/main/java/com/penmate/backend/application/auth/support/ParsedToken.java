package com.penmate.backend.application.auth.support;

public record ParsedToken(
        Long userId,
        String tokenId,
        String tokenType
) {
}

