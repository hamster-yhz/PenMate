package com.penmate.backend.application.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.command.ModelCommands.CreateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreateOfficialModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreateUserModelConfigCommand;
import com.penmate.backend.application.model.command.ModelCommands.SaveUserModelPreferencesCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateOfficialModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateUserModelConfigCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private SecretCryptoService secretCryptoService;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @Mock
    private IamGateway iamGateway;

    @InjectMocks
    private ModelApplicationService modelApplicationService;

    @Test
    void UT_APP_MODEL_LIST_PROVIDERS_SUCCESS() {
        List<ModelProvider> result = modelApplicationService.listProviders();

        assertThat(result).hasSize(BuiltinModelProviders.list().size());
        assertThat(result).extracting(ModelProvider::getCode).contains("openai", "xai", "deepseek", "openai-compatible");
        assertThat(result).extracting(ModelProvider::getProviderId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
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

        when(businessIdGenerator.nextId()).thenReturn(10001L);
        when(modelRepository.clearDefaultUserKey(userId)).thenReturn(1);
        when(secretCryptoService.encrypt("sk-123456")).thenReturn("cipher-123");
        when(modelRepository.insertUserKey(eq(10001L), eq(userId), eq(1L), eq("我的Key"), anyString(), anyString(), eq(true), eq("active")))
                .thenReturn(1);

        modelApplicationService.createKey(
                userId,
                new CreateModelKeyCommand(1L, "我的Key", "sk-123456", true, "active", operatorId),
                "trace"
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
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Failed to create model key");
    }

    @Test
    void UT_APP_MODEL_CREATE_KEY_MISSING_PROVIDER_ID_SHOULD_FAIL_FAST() {
        Long userId = 1001L;

        assertThatThrownBy(() -> modelApplicationService.createKey(
                userId,
                new CreateModelKeyCommand(null, "我的Key", "sk-123456", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Provider id is required");

        verify(modelRepository, never()).insertUserKey(anyLong(), eq(userId), anyLong(), anyString(), anyString(), anyString(), eq(false), eq("active"));
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_CREATE_KEY_UNKNOWN_PROVIDER_ID_SHOULD_FAIL_FAST() {
        Long userId = 1001L;

        assertThatThrownBy(() -> modelApplicationService.createKey(
                userId,
                new CreateModelKeyCommand(999L, "我的Key", "sk-123456", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Provider id is invalid");

        verify(modelRepository, never()).insertUserKey(anyLong(), eq(userId), anyLong(), anyString(), anyString(), anyString(), eq(false), eq("active"));
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_CREATE_OFFICIAL_KEY_SUCCESS() {
        Long operatorId = 1001L;

        when(businessIdGenerator.nextId()).thenReturn(10003L);
        when(modelRepository.clearDefaultOfficialKey(1L)).thenReturn(1);
        when(secretCryptoService.encrypt("sk-123456")).thenReturn("cipher-123");
        when(modelRepository.insertOfficialKey(eq(10003L), eq(1L), eq("官方Key"), eq("cipher-123"), anyString(), eq(true), eq("active")))
                .thenReturn(1);

        modelApplicationService.createOfficialKey(
                new CreateOfficialModelKeyCommand(1L, "官方Key", "sk-123456", true, "active", operatorId),
                "trace"
        );

        verify(modelRepository).clearDefaultOfficialKey(1L);
        verify(modelRepository).insertOfficialKey(eq(10003L), eq(1L), eq("官方Key"), eq("cipher-123"), anyString(), eq(true), eq("active"));
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_CREATE_OFFICIAL_KEY_MISSING_PROVIDER_ID_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.createOfficialKey(
                new CreateOfficialModelKeyCommand(null, "官方Key", "sk-123456", false, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Provider id is required");

        verify(modelRepository, never()).insertOfficialKey(anyLong(), anyLong(), anyString(), anyString(), anyString(), eq(false), eq("active"));
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_CREATE_USER_MODEL_CONFIG_WITH_DIRECT_USER_KEY_SUCCESS() {
        Long userId = 1001L;
        when(businessIdGenerator.nextId()).thenReturn(9001L);
        when(secretCryptoService.encrypt("sk-direct-user-key")).thenReturn("cipher-user-key");
        when(modelRepository.insertUserKey(eq(9001L), eq(userId), eq(1L), eq("gpt-4o-mini Key"), eq("cipher-user-key"), anyString(), eq(false), eq("active")))
                .thenReturn(1);
        when(modelRepository.findUserKey(9001L)).thenReturn(userKey(9001L, userId, 1L, "gpt-4o-mini Key", "active"));
        when(modelRepository.insertUserModelConfig(9001L, userId, 1L, "gpt-4o-mini", null, "USER_KEY", 9001L, null, 6, 128000, "active")).thenReturn(1);

        modelApplicationService.createUserModelConfig(
                userId,
                new CreateUserModelConfigCommand(1L, "gpt-4o-mini", null, "USER_KEY", "sk-direct-user-key", null, null, "active", 1001L),
                "trace"
        );

        verify(modelRepository).insertUserKey(eq(9001L), eq(userId), eq(1L), eq("gpt-4o-mini Key"), eq("cipher-user-key"), anyString(), eq(false), eq("active"));
        verify(modelRepository).insertUserModelConfig(9001L, userId, 1L, "gpt-4o-mini", null, "USER_KEY", 9001L, null, 6, 128000, "active");
    }

    @Test
    void UT_APP_MODEL_CREATE_OFFICIAL_MODEL_CONFIG_WITH_DIRECT_KEY_SUCCESS() {
        Long userId = 1001L;
        when(businessIdGenerator.nextId()).thenReturn(9001L);
        when(secretCryptoService.encrypt("sk-direct-official-key")).thenReturn("cipher-official-key");
        when(modelRepository.insertOfficialKey(eq(9001L), eq(1L), eq("gpt-4o-mini Key"), eq("cipher-official-key"), anyString(), eq(false), eq("active")))
                .thenReturn(1);
        when(modelRepository.findOfficialKey(9001L)).thenReturn(officialKey(9001L, 1L));
        when(modelRepository.insertUserModelConfig(9001L, userId, 1L, "gpt-4o-mini", null, "OFFICIAL_KEY", null, 9001L, 6, 128000, "active")).thenReturn(1);

        modelApplicationService.createUserModelConfig(
                userId,
                new CreateUserModelConfigCommand(1L, "gpt-4o-mini", null, "OFFICIAL_KEY", "sk-direct-official-key", null, null, "active", 1001L),
                "trace"
        );

        verify(modelRepository).insertOfficialKey(eq(9001L), eq(1L), eq("gpt-4o-mini Key"), eq("cipher-official-key"), anyString(), eq(false), eq("active"));
        verify(modelRepository).insertUserModelConfig(9001L, userId, 1L, "gpt-4o-mini", null, "OFFICIAL_KEY", null, 9001L, 6, 128000, "active");
    }

    @Test
    void UT_APP_MODEL_CREATE_USER_MODEL_CONFIG_WITH_EXPLICIT_MAX_CONTEXT_TOKENS_SUCCESS() {
        Long userId = 1001L;
        when(businessIdGenerator.nextId()).thenReturn(9002L);
        when(secretCryptoService.encrypt("sk-context-200k")).thenReturn("cipher-context-200k");
        when(modelRepository.insertUserKey(eq(9002L), eq(userId), eq(1L), eq("gpt-4.1 Key"), eq("cipher-context-200k"), anyString(), eq(false), eq("active")))
                .thenReturn(1);
        when(modelRepository.findUserKey(9002L)).thenReturn(userKey(9002L, userId, 1L, "gpt-4.1 Key", "active"));
        when(modelRepository.insertUserModelConfig(9002L, userId, 1L, "gpt-4.1", null, "USER_KEY", 9002L, null, 6, 200000, "active"))
                .thenReturn(1);

        modelApplicationService.createUserModelConfig(
                userId,
                new CreateUserModelConfigCommand(1L, "gpt-4.1", null, "USER_KEY", "sk-context-200k", 6, 200000, "active", 1001L),
                "trace"
        );

        verify(modelRepository).insertUserModelConfig(9002L, userId, 1L, "gpt-4.1", null, "USER_KEY", 9002L, null, 6, 200000, "active");
    }

    @Test
    void UT_APP_MODEL_CREATE_USER_MODEL_CONFIG_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.createUserModelConfig(1001L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_NOT_FOUND() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(null);

        assertThatThrownBy(() -> modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(1L, "gpt-4.1", null, "OFFICIAL_KEY", "sk-direct-official-key", null, null, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("User model config not found");
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_SWITCH_TO_DIRECT_KEY_SUCCESS() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "",
                "keySourceType", "USER_KEY",
                "userKeyId", 8001L,
                "status", "active"
        ));
        when(businessIdGenerator.nextId()).thenReturn(9101L);
        when(secretCryptoService.encrypt("sk-direct-official-key")).thenReturn("cipher-official-key");
        when(modelRepository.insertOfficialKey(eq(9101L), eq(1L), eq("gpt-4.1 Key"), eq("cipher-official-key"), anyString(), eq(false), eq("active")))
                .thenReturn(1);
        when(modelRepository.findOfficialKey(9101L)).thenReturn(officialKey(9101L, 1L));
        when(modelRepository.updateUserModelConfig(1001L, 9001L, 1L, "gpt-4.1", null, "OFFICIAL_KEY", null, 9101L, 6, 128000, "active")).thenReturn(1);

        modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(1L, "gpt-4.1", null, "OFFICIAL_KEY", "sk-direct-official-key", null, null, "active", 1001L),
                "trace"
        );

        verify(modelRepository).insertOfficialKey(eq(9101L), eq(1L), eq("gpt-4.1 Key"), eq("cipher-official-key"), anyString(), eq(false), eq("active"));
        verify(modelRepository).updateUserModelConfig(1001L, 9001L, 1L, "gpt-4.1", null, "OFFICIAL_KEY", null, 9101L, 6, 128000, "active");
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_SHOULD_PERSIST_EXPLICIT_MAX_CONTEXT_TOKENS() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "",
                "keySourceType", "USER_KEY",
                "userKeyId", 8001L,
                "contextWindowTurns", 6,
                "maxContextTokens", 128000,
                "status", "active"
        ));
        when(modelRepository.updateUserModelConfig(1001L, 9001L, 1L, "gpt-4o-mini", null, "USER_KEY", 8001L, null, 6, 200000, "active"))
                .thenReturn(1);

        modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(null, null, null, null, null, null, 200000, null, 1001L),
                "trace"
        );

        verify(modelRepository).updateUserModelConfig(1001L, 9001L, 1L, "gpt-4o-mini", null, "USER_KEY", 8001L, null, 6, 200000, "active");
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_WITH_EXISTING_USER_KEY_SHOULD_UPDATE_KEY_INSTEAD_OF_INSERTING_NEW_ONE() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "",
                "keySourceType", "USER_KEY",
                "userKeyId", 8001L,
                "status", "active"
        ));
        when(secretCryptoService.encrypt("sk-updated-user-key")).thenReturn("cipher-updated-user-key");
        when(modelRepository.updateUserKey(eq(1001L), eq(8001L), eq(null), eq("cipher-updated-user-key"), anyString(), eq(null), eq("active")))
                .thenReturn(1);
        when(modelRepository.updateUserModelConfig(1001L, 9001L, 1L, "gpt-4.1", null, "USER_KEY", 8001L, null, 6, 128000, "active")).thenReturn(1);

        modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(1L, "gpt-4.1", null, "USER_KEY", "sk-updated-user-key", null, null, "active", 1001L),
                "trace"
        );

        verify(modelRepository).updateUserKey(eq(1001L), eq(8001L), eq(null), eq("cipher-updated-user-key"), anyString(), eq(null), eq("active"));
        verify(modelRepository, never()).insertUserKey(anyLong(), eq(1001L), eq(1L), anyString(), anyString(), anyString(), eq(false), eq("active"));
        verify(modelRepository).updateUserModelConfig(1001L, 9001L, 1L, "gpt-4.1", null, "USER_KEY", 8001L, null, 6, 128000, "active");
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_WITH_ONLY_MODEL_NAME_PATCH_SHOULD_KEEP_EXISTING_FIELDS_AND_KEY_BINDING() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "https://api.openai.example",
                "keySourceType", "USER_KEY",
                "userKeyId", 8001L,
                "status", "active"
        ));
        when(modelRepository.updateUserModelConfig(
                1001L,
                9001L,
                1L,
                "gpt-4.1",
                "https://api.openai.example",
                "USER_KEY",
                8001L,
                null,
                6,
                128000,
                "active"
        )).thenReturn(1);

        modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(null, "gpt-4.1", null, null, null, null, null, null, 1001L),
                "trace"
        );

        verify(modelRepository, never()).updateUserKey(eq(1001L), eq(8001L), eq(null), anyString(), anyString(), eq(null), eq("active"));
        verify(modelRepository, never()).insertUserKey(anyLong(), eq(1001L), anyLong(), anyString(), anyString(), anyString(), eq(false), anyString());
        verify(modelRepository).updateUserModelConfig(
                1001L,
                9001L,
                1L,
                "gpt-4.1",
                "https://api.openai.example",
                "USER_KEY",
                8001L,
                null,
                6,
                128000,
                "active"
        );
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_WITH_EXISTING_OFFICIAL_KEY_SHOULD_UPDATE_KEY_INSTEAD_OF_INSERTING_NEW_ONE() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "",
                "keySourceType", "OFFICIAL_KEY",
                "officialKeyId", 7001L,
                "status", "active"
        ));
        when(secretCryptoService.encrypt("sk-updated-official-key")).thenReturn("cipher-updated-official-key");
        when(modelRepository.updateOfficialKey(eq(7001L), eq(null), eq("cipher-updated-official-key"), anyString(), eq(null), eq("active")))
                .thenReturn(1);
        when(modelRepository.updateUserModelConfig(1001L, 9001L, 1L, "gpt-4.1", null, "OFFICIAL_KEY", null, 7001L, 6, 128000, "active")).thenReturn(1);

        modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(1L, "gpt-4.1", null, "OFFICIAL_KEY", "sk-updated-official-key", null, null, "active", 1001L),
                "trace"
        );

        verify(modelRepository).updateOfficialKey(eq(7001L), eq(null), eq("cipher-updated-official-key"), anyString(), eq(null), eq("active"));
        verify(modelRepository, never()).insertOfficialKey(anyLong(), eq(1L), anyString(), anyString(), anyString(), eq(false), eq("active"));
        verify(modelRepository).updateUserModelConfig(1001L, 9001L, 1L, "gpt-4.1", null, "OFFICIAL_KEY", null, 7001L, 6, 128000, "active");
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_WHEN_PROVIDER_CHANGES_SHOULD_CREATE_NEW_USER_KEY() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "",
                "keySourceType", "USER_KEY",
                "userKeyId", 8001L,
                "status", "active"
        ));
        when(businessIdGenerator.nextId()).thenReturn(9201L);
        when(secretCryptoService.encrypt("sk-provider-changed-key")).thenReturn("cipher-provider-changed-key");
        when(modelRepository.insertUserKey(eq(9201L), eq(1001L), eq(2L), eq("gpt-4.1 Key"), eq("cipher-provider-changed-key"), anyString(), eq(false), eq("active")))
                .thenReturn(1);
        when(modelRepository.findUserKey(9201L)).thenReturn(userKey(9201L, 1001L, 2L, "gpt-4.1 Key", "active"));
        when(modelRepository.updateUserModelConfig(1001L, 9001L, 2L, "gpt-4.1", null, "USER_KEY", 9201L, null, 6, 128000, "active")).thenReturn(1);

        modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(2L, "gpt-4.1", null, "USER_KEY", "sk-provider-changed-key", null, null, "active", 1001L),
                "trace"
        );

        verify(modelRepository, never()).updateUserKey(eq(1001L), eq(8001L), eq(null), anyString(), anyString(), eq(null), eq("active"));
        verify(modelRepository).insertUserKey(eq(9201L), eq(1001L), eq(2L), eq("gpt-4.1 Key"), eq("cipher-provider-changed-key"), anyString(), eq(false), eq("active"));
        verify(modelRepository).updateUserModelConfig(1001L, 9001L, 2L, "gpt-4.1", null, "USER_KEY", 9201L, null, 6, 128000, "active");
    }

    @Test
    void UT_APP_MODEL_UPDATE_USER_MODEL_CONFIG_WHEN_PROVIDER_CHANGES_WITHOUT_NEW_API_KEY_SHOULD_FAIL() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "",
                "keySourceType", "USER_KEY",
                "userKeyId", 8001L,
                "status", "active"
        ));

        assertThatThrownBy(() -> modelApplicationService.updateUserModelConfig(
                1001L,
                9001L,
                new UpdateUserModelConfigCommand(2L, "gpt-4.1", null, "USER_KEY", null, null, null, "active", 1001L),
                "trace"
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Api key is required");

        verify(modelRepository, never()).updateUserModelConfig(1001L, 9001L, 2L, "gpt-4.1", null, "USER_KEY", 8001L, null, 6, 128000, "active");
        verify(modelRepository, never()).insertUserKey(anyLong(), eq(1001L), eq(2L), anyString(), anyString(), anyString(), eq(false), eq("active"));
    }

    @Test
    void UT_APP_MODEL_DELETE_USER_MODEL_CONFIG_NOT_FOUND() {
        when(modelRepository.softDeleteUserModelConfig(1001L, 9001L)).thenReturn(0);

        assertThatThrownBy(() -> modelApplicationService.deleteUserModelConfig(1001L, 9001L, 1001L, "trace"))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("User model config not found");
    }

    @Test
    void UT_APP_MODEL_UPDATE_KEY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updateKey(1001L, 2001L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void UT_APP_MODEL_UPDATE_OFFICIAL_KEY_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> modelApplicationService.updateOfficialKey(2001L, null, "trace"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
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
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Model key not found");
    }

    @Test
    void UT_APP_MODEL_DELETE_KEY_NOT_FOUND() {
        when(modelRepository.softDeleteUserKey(1001L, 9999L)).thenReturn(0);

        assertThatThrownBy(() -> modelApplicationService.deleteKey(1001L, 9999L, 1001L, "trace"))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("Model key not found");
    }

    @Test
    void UT_APP_MODEL_GET_USER_MODEL_PREFERENCES_DETAIL_SUCCESS() {
        Long userId = 1001L;
        IamUser user = new IamUser();
        user.setId(userId);
        user.setMainAgentModelConfigId(9001L);
        user.setDirtyWorkAgentModelConfigId(9002L);
        when(iamGateway.findUserByUserId(userId)).thenReturn(user);
        when(modelRepository.listUserModelConfigs(userId)).thenReturn(List.of(Map.of(
                "modelConfigId", 9001L,
                "modelName", "gpt-4o-mini",
                "providerId", 1L,
                "keySourceType", "USER_KEY",
                "keyName", "OpenAI User Key",
                "maskedApiKey", "****1234"
        )));

        Map<String, Object> result = modelApplicationService.getUserModelPreferencesDetail(userId);

        assertThat(result).containsEntry("mainAgentModelConfigId", 9001L)
                .containsEntry("dirtyWorkAgentModelConfigId", 9002L);
        assertThat((List<?>) result.get("candidateConfigs")).hasSize(1);
    }

    @Test
    void UT_APP_MODEL_LIST_USER_MODEL_CONFIGS_SUCCESS() {
        Long userId = 1001L;
        List<Map<String, Object>> expected = List.of(Map.of(
                "modelConfigId", 9001L,
                "modelName", "gpt-4o-mini",
                "providerId", 1L,
                "keySourceType", "USER_KEY",
                "keyName", "OpenAI User Key",
                "maskedApiKey", "****1234"
        ));
        when(modelRepository.listUserModelConfigs(userId)).thenReturn(expected);

        List<Map<String, Object>> result = modelApplicationService.listUserModelConfigs(userId);

        assertThat(result).isEqualTo(expected);
        verify(modelRepository).listUserModelConfigs(userId);
        verifyNoInteractions(auditService);
    }

    @Test
    void UT_APP_MODEL_LIST_USER_MODEL_CONFIGS_SHOULD_EXPOSE_CONTEXT_WINDOW_TURNS() {
        Long userId = 1001L;
        List<Map<String, Object>> expected = List.of(Map.of(
                "modelConfigId", 9001L,
                "modelName", "gpt-4o-mini",
                "providerId", 1L,
                "keySourceType", "USER_KEY",
                "keyName", "OpenAI User Key",
                "maskedApiKey", "****1234",
                "contextWindowTurns", 6
        ));
        when(modelRepository.listUserModelConfigs(userId)).thenReturn(expected);

        List<Map<String, Object>> result = modelApplicationService.listUserModelConfigs(userId);

        assertThat(result).isEqualTo(expected);
        assertThat(result.get(0)).containsEntry("contextWindowTurns", 6);
    }

    @Test
    void UT_APP_MODEL_SAVE_USER_MODEL_PREFERENCES_SUCCESS() {
        Long userId = 1001L;
        Long operatorId = 1002L;
        IamUser user = new IamUser();
        user.setUserId(userId);
        when(iamGateway.findUserByUserId(userId)).thenReturn(user);
        when(modelRepository.existsUsableModelConfig(userId, 9001L)).thenReturn(true);
        when(modelRepository.existsUsableModelConfig(userId, 9002L)).thenReturn(true);
        when(modelRepository.updateUserModelPreferences(userId, 9001L, 9002L)).thenReturn(1);

        modelApplicationService.saveUserModelPreferences(
                userId,
                operatorId,
                new SaveUserModelPreferencesCommand(9001L, 9002L),
                "trace"
        );

        verify(modelRepository).updateUserModelPreferences(userId, 9001L, 9002L);
    }

    @Test
    void UT_APP_MODEL_SAVE_USER_MODEL_PREFERENCES_USER_NOT_FOUND() {
        when(iamGateway.findUserByUserId(1001L)).thenReturn(null);

        assertThatThrownBy(() -> modelApplicationService.saveUserModelPreferences(
                1001L,
                1001L,
                new SaveUserModelPreferencesCommand(9001L, 9002L),
                "trace"
        )).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("User not found");
    }

    private ModelUserApiKey userKey(Long keyId, Long userId, Long providerId, String keyName, String status) {
        ModelUserApiKey key = new ModelUserApiKey();
        key.setUserApiKeyId(keyId);
        key.setUserId(userId);
        key.setProviderId(providerId);
        key.setKeyName(keyName);
        key.setStatus(status);
        return key;
    }

    private ModelOfficialApiKey officialKey(Long keyId, Long providerId) {
        ModelOfficialApiKey key = new ModelOfficialApiKey();
        key.setOfficialApiKeyId(keyId);
        key.setProviderId(providerId);
        key.setStatus("active");
        return key;
    }
}
