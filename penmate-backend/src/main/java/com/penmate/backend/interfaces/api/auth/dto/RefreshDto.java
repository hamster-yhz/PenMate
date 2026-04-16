package com.penmate.backend.interfaces.api.auth.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "刷新令牌请求参数")
@Data
public class RefreshDto {

    @Schema(description = "刷新令牌（refresh token）", example = "rtk_xxxxxxxxxxxxxxxxx", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String refreshToken;

}

