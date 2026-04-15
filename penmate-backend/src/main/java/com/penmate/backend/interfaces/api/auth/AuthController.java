package com.penmate.backend.interfaces.api.auth;

import com.penmate.backend.application.auth.AuthApplicationService;
import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.interfaces.api.auth.dto.LoginDto;
import com.penmate.backend.interfaces.api.auth.dto.RefreshDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AuthController。
 * <p>控制层：负责HTTP请求接入、参数校验与统一响应封装。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    /**
     * 执行登录流程。
     *
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginDto dto,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        LoginCommand command = new LoginCommand(dto.getEmail(), dto.getPassword());
        return ApiResponse.success(authApplicationService.login(command, traceId), traceId);
    }

    /**
     * 执行登出流程。
     *
     * @param authorization 入参：authorization
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/logout")
    public ApiResponse<String> logout(@RequestHeader("Authorization") String authorization,
                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        authApplicationService.logout(authorization, traceId);
        return ApiResponse.success("ok", traceId);
    }

    /**
     * 刷新鉴权凭证。
     *
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@Valid @RequestBody RefreshDto dto,
                                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        RefreshCommand command = new RefreshCommand(dto.getRefreshToken());
        return ApiResponse.success(authApplicationService.refresh(command, traceId), traceId);
    }

    /**
     * 处理业务请求。
     *
     * @param authorization 入参：authorization
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader("Authorization") String authorization,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(authApplicationService.me(authorization), traceId);
    }
}

