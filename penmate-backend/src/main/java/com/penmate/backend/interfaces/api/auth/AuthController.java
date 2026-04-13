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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginDto dto,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        LoginCommand command = new LoginCommand(dto.getEmail(), dto.getPassword());
        return ApiResponse.success(authApplicationService.login(command, traceId), traceId);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@RequestHeader("Authorization") String authorization,
                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        authApplicationService.logout(authorization, traceId);
        return ApiResponse.success("ok", traceId);
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@Valid @RequestBody RefreshDto dto,
                                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        RefreshCommand command = new RefreshCommand(dto.getRefreshToken());
        return ApiResponse.success(authApplicationService.refresh(command, traceId), traceId);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader("Authorization") String authorization,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(authApplicationService.me(authorization), traceId);
    }
}

