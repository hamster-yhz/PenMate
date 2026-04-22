package com.penmate.backend.application.model;

import com.penmate.backend.application.model.command.ModelCommands.CreateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreatePolicyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdatePolicyCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.application.model.BuiltinModelProviders;
import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private SecretCryptoService secretCryptoService;

    @InjectMocks
    private ModelApplicationService modelApplicationService;

    @Test
    void UT_APP_MODEL_LIST_PROVIDERS_SUCCESS() {
        List<ModelProvider> result = modelApplicationService.listProviders();

        assertThat(result).hasSize(BuiltinModelProviders.list().size());
        assertThat(result).extracting(ModelProvider::getCode).contains("openai", "xai", "deepseek");
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_LIST_USER_KEYS_SUCCESS() {
        Long userId = 1001L;
        when(modelRepository.listUserKeys(userId)).thenReturn(List.of(new ModelUserApiKey(), new ModelUserApiKey()));

        List<ModelUserApiKey> result = modelApplicationService.listUserKeys(userId);

        assertThat(result).hasSize(2);
        verify(modelRepository).listUserKeys(userId);
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_CREATE_KEY_SUCCESS() {
        Long userId = 1001L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-MODEL-CREATE-KEY";

        when(modelRepository.clearDefaultUserKey(userId)).thenReturn(1);
        when(secretCryptoService.encrypt("sk-123456")).thenReturn("cipher-123");
        when(modelRepository.insertUserKey(eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(true), eq("active")))
                .thenReturn(1);

        modelApplicationService.createKey(
                userId,
                new CreateModelKeyCommand(1L, "我的Key", "sk-123456", true, "active", operatorId),
                traceId
        );

        verify(modelRepository).clearDefaultUserKey(userId);
        verify(modelRepository).insertUserKey(eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(true), eq("active"));
    }

    @Test
    void UT_APP_MODEL_CREATE_KEY_FAILED() {
        Long userId = 1001L;
        when(modelRepository.insertUserKey(eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(false), eq("active")))
                .thenReturn(0);
        when(secretCryptoService.encrypt("sk-123456")).thenReturn("cipher-123");

        assertThatThrownBy(() -> modelApplicationService.createKey(
                userId,
                new CreateModelKeyCommand(1L, "我的Key", "sk-123456", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Failed to create model key");
    }

    @Test
    void UT_APP_MODEL_UPDATE_KEY_NOT_FOUND() {
        Long userId = 1001L;
        Long keyId = 9999L;
        when(secretCryptoService.encrypt("sk-654321")).thenReturn("cipher-654");
        when(modelRepository.updateUserKey(eq(userId), eq(keyId), eq("更新Key"), anyString(), anyString(), eq(false), eq("active")))
                .thenReturn(0);

        assertThatThrownBy(() -> modelApplicationService.updateKey(
                userId,
                keyId,
                new UpdateModelKeyCommand("更新Key", "sk-654321", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Model key not found");
    }

    @Test
    void UT_APP_MODEL_DELETE_KEY_NOT_FOUND() {
        when(modelRepository.softDeleteUserKey(1001L, 9999L)).thenReturn(0);

        assertThatThrownBy(() -> modelApplicationService.deleteKey(1001L, 9999L, 1001L, "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Model key not found");
    }

    @Test
    void UT_APP_MODEL_LIST_POLICIES_SUCCESS() {
        Long projectId = 1L;
        when(modelRepository.listProjectPolicies(projectId)).thenReturn(List.of(new ModelProjectPolicy(), new ModelProjectPolicy()));

        List<ModelProjectPolicy> result = modelApplicationService.listPolicies(projectId);

        assertThat(result).hasSize(2);
        verify(modelRepository).listProjectPolicies(projectId);
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_CREATE_POLICY_SUCCESS() {
        Long projectId = 1L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-MODEL-CREATE-POLICY";

        when(modelRepository.clearDefaultPolicy(projectId)).thenReturn(1);
        when(modelRepository.insertPolicy(
                eq(projectId),
                eq("默认策略"),
                eq("chat"),
                eq(1L),
                eq("gpt-4o-mini"),
                isNull(),
                eq(1L),
                isNull(),
                eq(new BigDecimal("0.7")),
                eq(new BigDecimal("0.9")),
                eq(2048),
                eq("{}"),
                eq(true)
        )).thenReturn(1);

        modelApplicationService.createPolicy(
                projectId,
                new CreatePolicyCommand(
                        "默认策略",
                        "chat",
                        1L,
                        "gpt-4o-mini",
                        null,
                        1L,
                        null,
                        new BigDecimal("0.7"),
                        new BigDecimal("0.9"),
                        2048,
                        "{}",
                        true,
                        operatorId
                ),
                traceId
        );

        verify(modelRepository).clearDefaultPolicy(projectId);
    }

    @Test
    void UT_APP_MODEL_UPDATE_POLICY_NOT_FOUND() {
        Long projectId = 1L;
        Long policyId = 9999L;
        when(modelRepository.updatePolicy(
                eq(projectId),
                eq(policyId),
                eq("更新策略"),
                eq("chat"),
                eq(2L),
                eq("gpt-4o"),
                isNull(),
                eq(2L),
                isNull(),
                eq(new BigDecimal("0.8")),
                eq(new BigDecimal("0.95")),
                eq(4096),
                eq("{\"fallback\":true}"),
                eq(false)
        )).thenReturn(0);

        assertThatThrownBy(() -> modelApplicationService.updatePolicy(
                projectId,
                policyId,
                new UpdatePolicyCommand(
                        "更新策略",
                        "chat",
                        2L,
                        "gpt-4o",
                        null,
                        2L,
                        null,
                        new BigDecimal("0.8"),
                        new BigDecimal("0.95"),
                        4096,
                        "{\"fallback\":true}",
                        false,
                        1001L
                ),
                "trace"
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Model policy not found");
    }

    @Test
    void UT_APP_MODEL_SET_DEFAULT_POLICY_NOT_FOUND() {
        when(modelRepository.setDefaultPolicy(1L, 9999L)).thenReturn(0);

        assertThatThrownBy(() -> modelApplicationService.setDefaultPolicy(1L, 9999L, 1001L, "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Model policy not found");
    }
}

