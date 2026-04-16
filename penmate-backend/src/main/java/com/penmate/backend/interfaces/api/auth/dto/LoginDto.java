package com.penmate.backend.interfaces.api.auth.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求参数")
@Data
public class LoginDto {

    @Schema(description = "登录邮箱（账号）", example = "user@penmate.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String email;

    @Schema(description = "登录密码", example = "P@ssw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String password;

}

