package com.penmate.backend.interfaces.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.auth.AuthApplicationService;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthApplicationService authApplicationService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    // 登录成功：返回 token 与统一响应 meta。
    void UT_AUTH_LOGIN_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AUTH-LOGIN-SUCCESS";
        when(authApplicationService.login(any(), eq(traceId))).thenReturn(Map.of(
                "accessToken", "atk_1",
                "refreshToken", "rtk_1"
        ));

        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "author@penmate.ai",
                                "password", "StrongPass!23"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("atk_1"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    // 登录参数缺失：触发 Bean Validation。
    void UT_AUTH_LOGIN_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-AUTH-LOGIN-INVALID-PARAM";

        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", "StrongPass!23"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 退出时 token 非法：映射为业务错误。
    void UT_AUTH_LOGOUT_INVALID_TOKEN() throws Exception {
        String traceId = "UT-TRACE-AUTH-LOGOUT-INVALID";
        doThrow(new IllegalArgumentException("Authorization header missing Bearer token"))
                .when(authApplicationService).logout(anyString(), eq(traceId));

        mockMvc().perform(post("/api/v1/auth/logout")
                        .header("Authorization", "invalid")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("Authorization header missing Bearer token"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 获取当前用户信息成功。
    void UT_AUTH_ME_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AUTH-ME-SUCCESS";
        when(authApplicationService.me(anyString())).thenReturn(Map.of(
                "id", 1001,
                "userId", 1001,
                "email", "author@penmate.ai",
                "displayName", "作者A"
        ));

        mockMvc().perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer atk_1")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.email").value("author@penmate.ai"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 登录凭证错误：服务层抛出业务异常。
    void UT_AUTH_LOGIN_BAD_CREDENTIAL() throws Exception {
        String traceId = "UT-TRACE-AUTH-LOGIN-BAD-CREDENTIAL";
        doThrow(new IllegalArgumentException("Bad credential"))
                .when(authApplicationService).login(any(), eq(traceId));

        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "author@penmate.ai",
                                "password", "Wrong!23"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("Bad credential"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // Refresh 成功：返回新 access token。
    void UT_AUTH_REFRESH_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AUTH-REFRESH-SUCCESS";
        when(authApplicationService.refresh(any(), eq(traceId))).thenReturn(Map.of(
                "accessToken", "atk_2",
                "refreshToken", "rtk_2"
        ));

        mockMvc().perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", "rtk_1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("atk_2"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // Refresh token 无效。
    void UT_AUTH_REFRESH_INVALID_TOKEN() throws Exception {
        String traceId = "UT-TRACE-AUTH-REFRESH-INVALID";
        doThrow(new IllegalArgumentException("Refresh token invalid"))
                .when(authApplicationService).refresh(any(), eq(traceId));

        mockMvc().perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", "bad_rtk"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("Refresh token invalid"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // Refresh 缺少 refreshToken 字段：应返回参数校验错误。
    void UT_API_AUTH_REFRESH_MISSING_REFRESH_TOKEN_BAD_REQUEST() throws Exception {
        String traceId = "UT-TRACE-AUTH-REFRESH-MISSING-REFRESH-TOKEN";

        mockMvc().perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.message").value("Refresh token is required"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 退出成功。
    void UT_AUTH_LOGOUT_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AUTH-LOGOUT-SUCCESS";
        doNothing().when(authApplicationService).logout("Bearer atk_1", traceId);

        mockMvc().perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer atk_1")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ok"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 获取当前用户失败（未授权/令牌无效）。
    void UT_AUTH_ME_UNAUTHORIZED() throws Exception {
        String traceId = "UT-TRACE-AUTH-ME-UNAUTHORIZED";
        doThrow(new IllegalArgumentException("Authorization header missing Bearer token"))
                .when(authApplicationService).me("invalid");

        mockMvc().perform(get("/api/v1/auth/me")
                        .header("Authorization", "invalid")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 登录失败：用户被禁用。
    void UT_AUTH_LOGIN_DISABLED_USER() throws Exception {
        String traceId = "UT-TRACE-AUTH-LOGIN-DISABLED";
        doThrow(new IllegalArgumentException("User disabled"))
                .when(authApplicationService).login(any(), eq(traceId));

        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "disabled@penmate.ai",
                                "password", "StrongPass!23"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("User disabled"));
    }

    @Test
    // 重复登出：依旧幂等返回成功。
    void UT_AUTH_LOGOUT_REPEAT() throws Exception {
        String traceId = "UT-TRACE-AUTH-LOGOUT-REPEAT";
        doNothing().when(authApplicationService).logout("Bearer atk_1", traceId);

        mockMvc().perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer atk_1")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ok"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // Refresh token 过期。
    void UT_AUTH_REFRESH_EXPIRED() throws Exception {
        String traceId = "UT-TRACE-AUTH-REFRESH-EXPIRED";
        doThrow(new IllegalArgumentException("Refresh token expired"))
                .when(authApplicationService).refresh(any(), eq(traceId));

        mockMvc().perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", "expired_rtk"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("Refresh token expired"));
    }
}

