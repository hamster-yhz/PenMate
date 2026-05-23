package com.penmate.backend.interfaces.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.ModelApplicationService;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void UT_MODEL_PROVIDER_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-PROVIDER-LIST";
        ModelProvider provider = new ModelProvider();
        provider.setId(90001L);
        provider.setProviderId(7001L);
        provider.setCode("openai-compatible");
        when(modelApplicationService.listProviders()).thenReturn(List.of(provider));

        mockMvc().perform(get("/api/v1/model/providers").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].providerId").isString())
                .andExpect(jsonPath("$.data[0].providerId").value("7001"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].code").value("openai-compatible"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_MODEL_PROVIDER_LIST_SHOULD_IGNORE_LEGACY_PHYSICAL_ID_ONLY_PROVIDER() throws Exception {
        String traceId = "UT-TRACE-MODEL-PROVIDER-LIST-PROVIDER-ID-ONLY";
        ModelProvider legacyOnlyProvider = new ModelProvider();
        legacyOnlyProvider.setId(99001L);
        legacyOnlyProvider.setCode("legacy-openai");
        ModelProvider validProvider = new ModelProvider();
        validProvider.setId(99002L);
        validProvider.setProviderId(7002L);
        validProvider.setCode("qwen-compatible");
        when(modelApplicationService.listProviders()).thenReturn(List.of(legacyOnlyProvider, validProvider));

        mockMvc().perform(get("/api/v1/model/providers").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].providerId").value("7002"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].code").value("qwen-compatible"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_MODEL_KEY_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE";
        doNothing().when(modelApplicationService).createKey(eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/keys")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "1",
                                "keyName", "my-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("created"));
    }

    @Test
    void UT_MODEL_KEY_CREATE_NUMERIC_PROVIDER_ID_SHOULD_BE_REJECTED() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE-NUMERIC-PROVIDER";

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details[0].field").value("requestBody"));
    }

    @Test
    void UT_MODEL_KEY_CREATE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE-INVALID";

        mockMvc().perform(post("/api/v1/model/keys")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "1",
                                "keyName", "",
                                "apiKey", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_KEY_CREATE_MISSING_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE-MISSING-PROVIDER";

        mockMvc().perform(post("/api/v1/model/keys")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "keyName", "my-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_KEY_CREATE_NON_POSITIVE_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-CREATE-NON-POSITIVE-PROVIDER";

        mockMvc().perform(post("/api/v1/model/keys")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "0",
                                "keyName", "my-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-CREATE";
        doNothing().when(modelApplicationService).createOfficialKey(any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/official-keys")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "1",
                                "keyName", "official-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("created"));

        verify(modelApplicationService).createOfficialKey(argThat(command ->
                        command.providerId().equals(1L)
                                && command.keyName().equals("official-key")
                                && command.apiKey().equals("sk-xxx")
                                && Boolean.TRUE.equals(command.isDefault())
                                && command.operatorId().equals(1001L)),
                eq(traceId));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-LIST";
        ModelOfficialApiKey key = new ModelOfficialApiKey();
        key.setId(7L);
        key.setOfficialApiKeyId(7701L);
        key.setProviderId(7001L);
        key.setKeyName("official-key");
        key.setEncryptedApiKey("cipher-value");
        key.setMaskedApiKey("sk-****-5678");
        key.setIsDefault(true);
        key.setStatus("active");
        when(modelApplicationService.listOfficialKeys()).thenReturn(List.of(key));

        mockMvc().perform(get("/api/v1/model/official-keys")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("7701"))
                .andExpect(jsonPath("$.data[0].providerId").value("7001"))
                .andExpect(jsonPath("$.data[0].officialApiKeyId").doesNotExist())
                .andExpect(jsonPath("$.data[0].encryptedApiKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].maskedApiKey").value("sk-****-5678"));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_CREATE_NUMERIC_PROVIDER_ID_SHOULD_BE_REJECTED() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-CREATE-NUMERIC-PROVIDER";

        mockMvc().perform(post("/api/v1/model/official-keys")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", 1,
                                "keyName", "official-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_CREATE_NEGATIVE_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-CREATE-NEGATIVE-PROVIDER";

        mockMvc().perform(post("/api/v1/model/official-keys")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "-1",
                                "keyName", "official-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_CREATE_ZERO_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-CREATE-ZERO-PROVIDER";

        mockMvc().perform(post("/api/v1/model/official-keys")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "0",
                                "keyName", "official-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_CREATE_MISSING_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-CREATE-MISSING-PROVIDER";

        mockMvc().perform(post("/api/v1/model/official-keys")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "keyName", "official-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_CREATE_NULL_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-CREATE-NULL-PROVIDER";

        mockMvc().perform(post("/api/v1/model/official-keys")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content("{\"providerId\":null,\"keyName\":\"official-key\",\"apiKey\":\"sk-xxx\",\"isDefault\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_OFFICIAL_KEY_CREATE_UNKNOWN_PROVIDER_ID_BUSINESS_ERROR() throws Exception {
        String traceId = "UT-TRACE-MODEL-OFFICIAL-KEY-CREATE-UNKNOWN-PROVIDER";
        doThrow(BusinessException.of("Provider id is invalid"))
                .when(modelApplicationService).createOfficialKey(any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/official-keys")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "999",
                                "keyName", "official-key",
                                "apiKey", "sk-xxx",
                                "isDefault", true
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.data.message").value("Provider id is invalid"));
    }

    @Test
    void UT_MODEL_KEY_MASKED_FIELD_ASSERT() throws Exception {
        String traceId = "UT-TRACE-MODEL-KEY-MASKED";
        ModelUserApiKey key = new ModelUserApiKey();
        key.setId(8L);
        key.setUserApiKeyId(8801L);
        key.setKeyName("my-key");
        key.setMaskedApiKey("sk-****-1234");
        when(modelApplicationService.listUserKeys(1001L)).thenReturn(List.of(key));

        mockMvc().perform(get("/api/v1/model/keys")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("8801"))
                .andExpect(jsonPath("$.data[0].userApiKeyId").doesNotExist())
                .andExpect(jsonPath("$.data[0].maskedApiKey").value("sk-****-1234"));
    }

    @Test
    void UT_MODEL_USER_CONFIG_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-LIST";
        when(modelApplicationService.listUserModelConfigs(1001L)).thenReturn(List.of(Map.of(
                "modelConfigId", 9001L,
                "modelName", "gpt-4o-mini",
                "providerId", 1L,
                "keySourceType", "USER_KEY",
                "keyName", "OpenAI User Key",
                "maskedApiKey", "****1234"
        )));

        mockMvc().perform(get("/api/v1/model/configs")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].modelConfigId").value("9001"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].modelName").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data[0].keySourceType").value("USER_KEY"))
                .andExpect(jsonPath("$.data[0].keyName").value("OpenAI User Key"));
    }

    @Test
    void UT_MODEL_USER_CONFIG_CREATE_ACCEPTS_DIRECT_KEY_VALUE() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-CREATE";
        doNothing().when(modelApplicationService).createUserModelConfig(eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/configs")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "1",
                                "modelName", "gpt-4o-mini",
                                "modelCategory", "USER_MODEL",
                                "apiKey", "sk-direct-user-key",
                                "status", "active"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("created"));

        verify(modelApplicationService).createUserModelConfig(
                eq(1001L),
                argThat(command -> command.providerId().equals(1L)
                        && command.modelName().equals("gpt-4o-mini")
                        && command.baseUrl() == null
                        && command.keySourceType().equals("USER_KEY")
                        && command.apiKey().equals("sk-direct-user-key")
                        && command.status().equals("active")
                        && command.operatorId().equals(1001L)),
                eq(traceId));
    }

    @Test
    void UT_MODEL_USER_CONFIG_CREATE_ACCEPTS_MAX_CONTEXT_TOKENS() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-CREATE-MAX-CONTEXT";
        doNothing().when(modelApplicationService).createUserModelConfig(eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/configs")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "1",
                                "modelName", "gpt-4.1",
                                "modelCategory", "USER_MODEL",
                                "apiKey", "sk-context-200k",
                                "contextWindowTurns", 6,
                                "maxContextTokens", 200000,
                                "status", "active"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("created"));

        verify(modelApplicationService).createUserModelConfig(
                eq(1001L),
                argThat(command -> command.providerId().equals(1L)
                        && command.modelName().equals("gpt-4.1")
                        && command.contextWindowTurns().equals(6)
                        && command.maxContextTokens().equals(200000)
                        && command.operatorId().equals(1001L)),
                eq(traceId));
    }

    @Test
    void UT_MODEL_USER_CONFIG_CREATE_NON_POSITIVE_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-CREATE-NON-POSITIVE-PROVIDER";

        mockMvc().perform(post("/api/v1/model/configs")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "0",
                                "modelName", "gpt-4o-mini",
                                "modelCategory", "USER_MODEL",
                                "apiKey", "sk-direct-user-key"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_USER_CONFIG_CREATE_NUMERIC_PROVIDER_ID_SHOULD_BE_REJECTED() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-CREATE-NUMERIC-PROVIDER";

        mockMvc().perform(post("/api/v1/model/configs")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", 1,
                                "modelName", "gpt-4o-mini",
                                "modelCategory", "USER_MODEL",
                                "apiKey", "sk-direct-user-key"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details[0].field").value("requestBody"));
    }

    @Test
    void UT_MODEL_USER_CONFIG_UPDATE_ACCEPTS_DIRECT_KEY_VALUE() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-UPDATE";
        doNothing().when(modelApplicationService).updateUserModelConfig(eq(1001L), eq(9001L), any(), eq(traceId));

        mockMvc().perform(put("/api/v1/model/configs/{modelConfigId}", "9001")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "2",
                                "modelName", "gpt-4.1",
                                "modelCategory", "OFFICIAL_MODEL",
                                "apiKey", "sk-direct-official-key",
                                "status", "active"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));

        verify(modelApplicationService).updateUserModelConfig(
                eq(1001L),
                eq(9001L),
                argThat(command -> command.providerId().equals(2L)
                        && command.modelName().equals("gpt-4.1")
                        && command.baseUrl() == null
                        && command.keySourceType().equals("OFFICIAL_KEY")
                        && command.apiKey().equals("sk-direct-official-key")
                        && command.status().equals("active")
                        && command.operatorId().equals(1001L)),
                eq(traceId));
    }

    @Test
    void UT_MODEL_USER_CONFIG_UPDATE_ACCEPTS_MAX_CONTEXT_TOKENS() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-UPDATE-MAX-CONTEXT";
        doNothing().when(modelApplicationService).updateUserModelConfig(eq(1001L), eq(9001L), any(), eq(traceId));

        mockMvc().perform(put("/api/v1/model/configs/{modelConfigId}", "9001")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "maxContextTokens", 32000,
                                "status", "active"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));

        verify(modelApplicationService).updateUserModelConfig(
                eq(1001L),
                eq(9001L),
                argThat(command -> command.maxContextTokens().equals(32000)
                        && command.operatorId().equals(1001L)),
                eq(traceId));
    }

    @Test
    void UT_MODEL_USER_CONFIG_UPDATE_NON_POSITIVE_PROVIDER_ID_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-UPDATE-NON-POSITIVE-PROVIDER";

        mockMvc().perform(put("/api/v1/model/configs/{modelConfigId}", "9001")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", "0",
                                "modelName", "gpt-4.1",
                                "modelCategory", "OFFICIAL_MODEL",
                                "apiKey", "sk-direct-official-key"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_USER_CONFIG_UPDATE_NUMERIC_PROVIDER_ID_SHOULD_BE_REJECTED() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-UPDATE-NUMERIC-PROVIDER";

        mockMvc().perform(put("/api/v1/model/configs/{modelConfigId}", "9001")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", 2,
                                "modelName", "gpt-4.1",
                                "modelCategory", "OFFICIAL_MODEL",
                                "apiKey", "sk-direct-official-key"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details[0].field").value("requestBody"));
    }

    @Test
    void UT_MODEL_USER_CONFIG_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-CONFIG-DELETE";
        doNothing().when(modelApplicationService).deleteUserModelConfig(1001L, 9001L, 1001L, traceId);

        mockMvc().perform(delete("/api/v1/model/configs/{modelConfigId}", "9001")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"));
    }

    @Test
    void UT_MODEL_PREFERENCES_DETAIL_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-DETAIL";
        when(modelApplicationService.getUserModelPreferencesDetail(1001L)).thenReturn(Map.of(
                "mainAgentModelConfigId", 9001L,
                "dirtyWorkAgentModelConfigId", 9002L,
                "candidateConfigs", List.of(Map.of(
                        "modelConfigId", 9001L,
                        "modelName", "gpt-4o-mini",
                        "providerId", 1,
                        "keySourceType", "USER_KEY",
                        "keyName", "OpenAI User Key",
                        "maskedApiKey", "****1234"
                ))
        ));

        mockMvc().perform(get("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mainAgentModelConfigId").value("9001"))
                .andExpect(jsonPath("$.data.dirtyWorkAgentModelConfigId").value("9002"))
                .andExpect(jsonPath("$.data.candidateConfigs[0].modelConfigId").value("9001"))
                .andExpect(jsonPath("$.data.candidateConfigs[0].providerId").value("1"));
    }

    @Test
    void UT_MODEL_PREFERENCES_DETAIL_USER_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-DETAIL-NOT-FOUND";
        doThrow(new IllegalArgumentException("User not found"))
                .when(modelApplicationService).getUserModelPreferencesDetail(1001L);

        mockMvc().perform(get("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void UT_MODEL_PREFERENCES_SAVE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-SAVE";
        doNothing().when(modelApplicationService).saveUserModelPreferences(eq(1001L), eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mainAgentModelConfigId", "9001",
                                "dirtyWorkAgentModelConfigId", "9002"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));
    }

    @Test
    void UT_MODEL_PREFERENCES_SAVE_EMPTY_STRING_SHOULD_CLEAR_PREFERENCES() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-SAVE-EMPTY";
        doNothing().when(modelApplicationService).saveUserModelPreferences(eq(1001L), eq(1001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mainAgentModelConfigId", "",
                                "dirtyWorkAgentModelConfigId", ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));

        verify(modelApplicationService).saveUserModelPreferences(
                eq(1001L),
                eq(1001L),
                argThat(command -> command.mainAgentModelConfigId() == null
                        && command.dirtyWorkAgentModelConfigId() == null),
                eq(traceId));
    }

    @Test
    void UT_MODEL_PREFERENCES_SAVE_NUMERIC_IDS_SHOULD_BE_REJECTED() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-SAVE-NUMERIC";

        mockMvc().perform(post("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mainAgentModelConfigId", 9001,
                                "dirtyWorkAgentModelConfigId", 9002
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_PREFERENCES_SAVE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-MODEL-PREFERENCES-SAVE-INVALID";

        mockMvc().perform(post("/api/v1/model/preferences")
                        .param("userId", "1001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mainAgentModelConfigId", "-1",
                                "dirtyWorkAgentModelConfigId", "0"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_MODEL_LEGACY_PROJECT_POLICY_ENDPOINT_SHOULD_NOT_BE_EXPOSED() throws Exception {
        String legacyPath = "/api/v1/novels/920001/model" + "-" + "policies";
        mockMvc().perform(get(legacyPath))
                .andExpect(status().isNotFound());
    }
}
