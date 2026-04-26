package com.penmate.backend.application.model;

import com.penmate.backend.application.model.command.ModelCommands.CreateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreateOfficialModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreatePolicyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdatePolicyCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.application.model.BuiltinModelProviders;
import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private SecretCryptoService secretCryptoService;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

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

        when(businessIdGenerator.nextId()).thenReturn(10001L);
        when(modelRepository.clearDefaultUserKey(userId)).thenReturn(1);
        when(secretCryptoService.encrypt("sk-123456")).thenReturn("cipher-123");
        when(modelRepository.insertUserKey(eq(10001L), eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(true), eq("active")))
                .thenReturn(1);

        modelApplicationService.createKey(
                userId,
                new CreateModelKeyCommand(1L, "我的Key", "sk-123456", true, "active", operatorId),
                traceId
        );

        verify(modelRepository).clearDefaultUserKey(userId);
        verify(modelRepository).insertUserKey(eq(10001L), eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(true), eq("active"));
    }

    @Test
    void UT_APP_MODEL_CREATE_KEY_FAILED() {
        Long userId = 1001L;
        when(businessIdGenerator.nextId()).thenReturn(10002L);
        when(modelRepository.insertUserKey(eq(10002L), eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(false), eq("active")))
                .thenReturn(0);
        when(secretCryptoService.encrypt("sk-123456")).thenReturn("cipher-123");

        assertThatThrownBy(() -> modelApplicationService.createKey(
                userId,
                new CreateModelKeyCommand(1L, "我的Key", "sk-123456", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Failed to create model key");

        verify(businessIdGenerator).nextId();
        verify(modelRepository).insertUserKey(eq(10002L), eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(false), eq("active"));
    }

    @Test
    void UT_APP_MODEL_CREATE_KEY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.createKey(1001L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_CREATE_KEY_NULL_USER_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.createKey(
                null,
                new CreateModelKeyCommand(1L, "我的Key", "sk-123456", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("userId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_CREATE_OFFICIAL_KEY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.createOfficialKey((CreateOfficialModelKeyCommand) null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_CREATE_POLICY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.createPolicy(1L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_CREATE_POLICY_NULL_PROJECT_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.createPolicy(
                null,
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
                        1001L
                ),
                "trace"
        )).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("projectId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_UPDATE_KEY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updateKey(1001L, 2001L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_UPDATE_OFFICIAL_KEY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updateOfficialKey(2001L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_UPDATE_POLICY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updatePolicy(1L, 3001L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_UPDATE_POLICY_NULL_PROJECT_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updatePolicy(
                null,
                3001L,
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
        )).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("projectId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
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
    void UT_APP_MODEL_UPDATE_KEY_NULL_USER_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updateKey(
                null,
                9999L,
                new UpdateModelKeyCommand("更新Key", "sk-654321", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("userId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_UPDATE_KEY_NULL_KEY_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updateKey(
                1001L,
                null,
                new UpdateModelKeyCommand("更新Key", "sk-654321", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("keyId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_DELETE_KEY_NOT_FOUND() {
        when(modelRepository.softDeleteUserKey(1001L, 9999L)).thenReturn(0);

        assertThatThrownBy(() -> modelApplicationService.deleteKey(1001L, 9999L, 1001L, "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Model key not found");
    }

    @Test
    void UT_APP_MODEL_DELETE_KEY_NULL_USER_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.deleteKey(null, 9999L, 1001L, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("userId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_DELETE_KEY_NULL_KEY_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.deleteKey(1001L, null, 1001L, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("keyId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_DELETE_KEY_NULL_OPERATOR_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.deleteKey(1001L, 9999L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("operatorId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
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

        when(businessIdGenerator.nextId()).thenReturn(20001L);
        when(modelRepository.clearDefaultPolicy(projectId)).thenReturn(1);
        when(modelRepository.insertPolicy(
                eq(20001L),
                eq(projectId),
                eq("默认策略"),
                eq("chat"),
                eq(1L),
                eq("gpt-4o-mini"),
                org.mockito.ArgumentMatchers.<String>isNull(),
                eq(1L),
                org.mockito.ArgumentMatchers.<Long>isNull(),
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
        verify(modelRepository).insertPolicy(
                eq(20001L),
                eq(projectId),
                eq("默认策略"),
                eq("chat"),
                eq(1L),
                eq("gpt-4o-mini"),
                org.mockito.ArgumentMatchers.<String>isNull(),
                eq(1L),
                org.mockito.ArgumentMatchers.<Long>isNull(),
                eq(new BigDecimal("0.7")),
                eq(new BigDecimal("0.9")),
                eq(2048),
                eq("{}"),
                eq(true)
        );
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
                org.mockito.ArgumentMatchers.<String>isNull(),
                eq(2L),
                org.mockito.ArgumentMatchers.<Long>isNull(),
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
        when(modelRepository.findProjectPolicy(1L, 9999L)).thenReturn(null);

        assertThatThrownBy(() -> modelApplicationService.setDefaultPolicy(1L, 9999L, 1001L, "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Model policy not found");

        verify(modelRepository, never()).clearDefaultPolicy(1L);
        verify(modelRepository, never()).setDefaultPolicy(1L, 9999L);
    }

    @Test
    void UT_APP_MODEL_SET_DEFAULT_POLICY_NULL_OPERATOR_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.setDefaultPolicy(1L, 9999L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("operatorId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_SET_DEFAULT_POLICY_NULL_PROJECT_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.setDefaultPolicy(null, 9999L, 1001L, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("projectId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_SET_DEFAULT_POLICY_NULL_POLICY_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.setDefaultPolicy(1L, null, 1001L, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("policyId must not be null");

        verifyNoInteractions(modelRepository, secretCryptoService, businessIdGenerator);
    }

    @Test
    void UT_APP_MODEL_UPDATE_KEY_BLANK_API_KEY_SHOULD_NOT_ENCRYPT_AND_SHOULD_PERSIST_NULL_SECRET_FIELDS() {
        Long userId = 1001L;
        Long keyId = 2001L;

        when(modelRepository.updateUserKey(eq(userId), eq(keyId), eq("更新Key"), org.mockito.ArgumentMatchers.<String>isNull(), org.mockito.ArgumentMatchers.<String>isNull(), eq(false), eq("inactive")))
                .thenReturn(1);

        modelApplicationService.updateKey(
                userId,
                keyId,
                new UpdateModelKeyCommand("更新Key", "   ", false, "inactive", 1001L),
                "trace"
        );

        verify(secretCryptoService, never()).encrypt(anyString());
        verify(modelRepository).updateUserKey(eq(userId), eq(keyId), eq("更新Key"), org.mockito.ArgumentMatchers.<String>isNull(), org.mockito.ArgumentMatchers.<String>isNull(), eq(false), eq("inactive"));
    }

    @Test
    void UT_APP_MODEL_UPDATE_OFFICIAL_KEY_NOT_FOUND_BEFORE_UPDATE() {
        when(modelRepository.findOfficialKey(3001L)).thenReturn(null);

        assertThatThrownBy(() -> modelApplicationService.updateOfficialKey(
                3001L,
                new com.penmate.backend.application.model.command.ModelCommands.UpdateOfficialModelKeyCommand("官方Key", "sk-official", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Official model key not found");

        verify(modelRepository).findOfficialKey(3001L);
        verify(modelRepository, never()).updateOfficialKey(anyLong(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void UT_APP_MODEL_SET_DEFAULT_POLICY_SUCCESS() {
        Long projectId = 1L;
        Long policyId = 3001L;
        ModelProjectPolicy existing = new ModelProjectPolicy();
        existing.setProjectPolicyId(policyId);
        when(modelRepository.findProjectPolicy(projectId, policyId)).thenReturn(existing);
        when(modelRepository.clearDefaultPolicy(projectId)).thenReturn(1);
        when(modelRepository.setDefaultPolicy(projectId, policyId)).thenReturn(1);

        modelApplicationService.setDefaultPolicy(projectId, policyId, 1001L, "trace");

        verify(modelRepository).findProjectPolicy(projectId, policyId);
        verify(modelRepository).clearDefaultPolicy(projectId);
        verify(modelRepository).setDefaultPolicy(projectId, policyId);
    }
}

