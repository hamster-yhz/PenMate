package com.penmate.backend.interfaces.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshDto {
    @NotBlank
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}

