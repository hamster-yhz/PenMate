package com.penmate.backend.interfaces.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.model.ModelApplicationService;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ModelControllerTest {

    @Mock
    private ModelApplicationService modelApplicationService;

    @InjectMocks
    private ModelController modelController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(modelController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    // 供应商列表查询成功。
    void UT_MODEL_PROVIDER_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-PROVIDER-LIST";
        ModelProvider provider = new ModelProvider();
        provider.setId(1L);
        provider.setCode("openai-compatible");
        when(modelApplicationService.listProviders()).thenReturn(List.of(provider));

        mockMvc().perform(get("/api/v1/model/providers").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("openai-compatible"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 创建用户密钥成功。
    void UT_MODEL_KEY_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE";
        doNothing().when(modelApplicationService).createKey(eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/keys")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", 1,
                                "keyName", "my-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("created"));
    }

    @Test
    // 创建用户密钥参数错误。
    void UT_MODEL_KEY_CREATE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE-INVALID";

        mockMvc().perform(post("/api/v1/model/keys")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", 1,
                                "keyName", "",
                                "apiKey", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400)) 
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    // 创建用户密钥缺少 operatorId 参数：应返回参数校验错误。
    void UT_MODEL_KEY_CREATE_MISSING_OPERATOR_ID_BAD_REQUEST() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE-MISSING-OPERATOR-ID";

        mockMvc().perform(post("/api/v1/model/keys")
                        .param("userId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", 1,
                                "keyName", "my-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details[0].field").value("operatorId"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 列表密钥掩码字段断言。
    void UT_MODEL_KEY_MASKED_FIELD_ASSERT() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-MASKED";
        ModelUserApiKey key = new ModelUserApiKey();
        key.setId(8L);
        key.setKeyName("my-key");
        key.setMaskedApiKey("sk-****-1234");
        when(modelApplicationService.listUserKeys(1001L)).thenReturn(List.of(key));

        mockMvc().perform(get("/api/v1/model/keys")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].maskedApiKey").value("sk-****-1234"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 设置默认策略冲突。
    void UT_MODEL_POLICY_SET_DEFAULT_CONFLICT() throws Exception {
        String traceId = "UT-TRACE-MODEL-POLICY-SET-DEFAULT-CONFLICT";
        doThrow(new IllegalArgumentException("Policy not found"))
                .when(modelApplicationService).setDefaultPolicy(10001L, 999L, 1001L, traceId);

        mockMvc().perform(post("/api/v1/novels/10001/model-policies/999/set-default")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    // 更新用户密钥成功。
    void UT_MODEL_KEY_UPDATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-UPDATE";
        doNothing().when(modelApplicationService).updateKey(eq(1001L), eq(8L), any(), eq(traceId));

        mockMvc().perform(patch("/api/v1/model/keys/8")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "keyName", "my-key-updated",
                                "isDefault", true,
                                "status", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));
    }

    @Test
    // 删除用户密钥成功。
    void UT_MODEL_KEY_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-DELETE";
        doNothing().when(modelApplicationService).deleteKey(1001L, 8L, 1001L, traceId);

        mockMvc().perform(delete("/api/v1/model/keys/8")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 创建模型策略成功。
    void UT_MODEL_POLICY_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-POLICY-CREATE";
        doNothing().when(modelApplicationService).createPolicy(eq(10001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/model-policies")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "policyName", "默认策略",
                                "scene", "draft",
                                "providerModelId", 11,
                                "modelName", "gpt-4o-mini",
                                "userKeyId", 8,
                                "temperature", 0.7,
                                "topP", 0.9,
                                "maxTokens", 2048,
                                "isDefault", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("created"));
    }

    @Test
    // 设置默认策略成功。
    void UT_MODEL_POLICY_SET_DEFAULT_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-POLICY-SET-DEFAULT-SUCCESS";
        doNothing().when(modelApplicationService).setDefaultPolicy(10001L, 201L, 1001L, traceId);

        mockMvc().perform(post("/api/v1/novels/10001/model-policies/201/set-default")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));
    }

    @Test
    // 用户模型偏好读取契约成功：返回当前偏好与候选配置列表。
    void UT_MODEL_PREFERENCES_DETAIL_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-DETAIL";
        when(modelApplicationService.getUserModelPreferencesDetail(1001L)).thenReturn(Map.of(
                "mainAgentModelConfigId", 9001L,
                "dirtyWorkAgentModelConfigId", 9002L,
                "candidateConfigs", List.of(Map.of(
                        "modelConfigId", 9001L,
                        "modelName", "gpt-4o-mini",
                        "providerId", 1,
                        "keySourceType", "USER_KEY"
                ))
        ));

        mockMvc().perform(get("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mainAgentModelConfigId").value(9001L))
                .andExpect(jsonPath("$.data.dirtyWorkAgentModelConfigId").value(9002L))
                .andExpect(jsonPath("$.data.candidateConfigs[0].modelConfigId").value(9001L))
                .andExpect(jsonPath("$.data.candidateConfigs[0].modelName").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 查询用户模型偏好时用户不存在，应返回业务错误。
    void UT_MODEL_PREFERENCES_DETAIL_USER_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-DETAIL-NOT-FOUND";
        doThrow(new IllegalArgumentException("User not found"))
                .when(modelApplicationService).getUserModelPreferencesDetail(1001L);

        mockMvc().perform(get("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 保存用户模型偏好成功。
    void UT_MODEL_PREFERENCES_SAVE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-SAVE";
        doNothing().when(modelApplicationService).saveUserModelPreferences(eq(1001L), eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mainAgentModelConfigId", 9001,
                                "dirtyWorkAgentModelConfigId", 9002
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));
    }

    @Test
    // 保存用户模型偏好时配置不可用，应返回业务错误。
    void UT_MODEL_PREFERENCES_SAVE_CONFIG_UNAVAILABLE() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-SAVE-UNAVAILABLE";
        doThrow(new IllegalArgumentException("Main agent model config is unavailable"))
                .when(modelApplicationService).saveUserModelPreferences(eq(1001L), eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mainAgentModelConfigId", 9001,
                                "dirtyWorkAgentModelConfigId", 9002
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 保存用户模型偏好参数非法时应返回校验错误。
    void UT_MODEL_PREFERENCES_SAVE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-SAVE-INVALID";

        mockMvc().perform(post("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mainAgentModelConfigId", -1,
                                "dirtyWorkAgentModelConfigId", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }
}

