package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ModelMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface ModelMapper {

    @Select("""
            SELECT id, user_api_key_id, user_id, provider_id, key_name, encrypted_api_key, masked_api_key,
                   is_default, last_used_at, status, created_at, updated_at, deleted_at
            FROM model_user_api_keys
            WHERE user_id = #{userId} AND deleted_at IS NULL
            ORDER BY is_default DESC, id DESC
            """)
    List<ModelUserApiKey> listUserKeys(@Param("userId") Long userId);

    @Select("""
            SELECT id, official_api_key_id, provider_id, key_name, encrypted_api_key, masked_api_key,
                   is_default, last_used_at, status, created_at, updated_at, deleted_at
            FROM model_official_api_keys
            WHERE deleted_at IS NULL
            ORDER BY provider_id ASC, is_default DESC, id DESC
            """)
    List<ModelOfficialApiKey> listOfficialKeys();

    @Insert("""
            INSERT INTO model_user_api_keys(user_api_key_id, user_id, provider_id, key_name, encrypted_api_key, masked_api_key, is_default, status)
            VALUES (#{userApiKeyId}, #{userId}, #{providerId}, #{keyName}, #{encryptedApiKey}, #{maskedApiKey}, #{isDefault}, #{status})
            """)
    int insertUserKey(@Param("userApiKeyId") Long userApiKeyId,
                      @Param("userId") Long userId,
                      @Param("providerId") Long providerId,
                      @Param("keyName") String keyName,
                      @Param("encryptedApiKey") String encryptedApiKey,
                      @Param("maskedApiKey") String maskedApiKey,
                      @Param("isDefault") boolean isDefault,
                      @Param("status") String status);

    @Insert("""
            INSERT INTO model_official_api_keys(official_api_key_id, provider_id, key_name, encrypted_api_key, masked_api_key, is_default, status)
            VALUES (#{officialApiKeyId}, #{providerId}, #{keyName}, #{encryptedApiKey}, #{maskedApiKey}, #{isDefault}, #{status})
            """)
    int insertOfficialKey(@Param("officialApiKeyId") Long officialApiKeyId,
                          @Param("providerId") Long providerId,
                          @Param("keyName") String keyName,
                          @Param("encryptedApiKey") String encryptedApiKey,
                          @Param("maskedApiKey") String maskedApiKey,
                          @Param("isDefault") boolean isDefault,
                          @Param("status") String status);

    @Update("""
            UPDATE model_user_api_keys
            SET is_default = 0
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int clearDefaultUserKey(@Param("userId") Long userId);

    @Update("""
            UPDATE model_official_api_keys
            SET is_default = 0,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE provider_id = #{providerId} AND deleted_at IS NULL
            """)
    int clearDefaultOfficialKey(@Param("providerId") Long providerId);

    @Update("""
            UPDATE model_user_api_keys
            SET key_name = COALESCE(#{keyName}, key_name),
                encrypted_api_key = COALESCE(#{encryptedApiKey}, encrypted_api_key),
                masked_api_key = COALESCE(#{maskedApiKey}, masked_api_key),
                is_default = COALESCE(#{isDefault}, is_default),
                status = COALESCE(#{status}, status),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND user_api_key_id = #{keyId} AND deleted_at IS NULL
            """)
    int updateUserKey(@Param("userId") Long userId,
                      @Param("keyId") Long keyId,
                      @Param("keyName") String keyName,
                      @Param("encryptedApiKey") String encryptedApiKey,
                      @Param("maskedApiKey") String maskedApiKey,
                      @Param("isDefault") Boolean isDefault,
                      @Param("status") String status);

    @Update("""
            UPDATE model_official_api_keys
            SET key_name = COALESCE(#{keyName}, key_name),
                encrypted_api_key = COALESCE(#{encryptedApiKey}, encrypted_api_key),
                masked_api_key = COALESCE(#{maskedApiKey}, masked_api_key),
                is_default = COALESCE(#{isDefault}, is_default),
                status = COALESCE(#{status}, status),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE official_api_key_id = #{keyId} AND deleted_at IS NULL
            """)
    int updateOfficialKey(@Param("keyId") Long keyId,
                          @Param("keyName") String keyName,
                          @Param("encryptedApiKey") String encryptedApiKey,
                          @Param("maskedApiKey") String maskedApiKey,
                          @Param("isDefault") Boolean isDefault,
                          @Param("status") String status);

    @Update("""
            UPDATE model_user_api_keys
            SET deleted_at = CURRENT_TIMESTAMP(3),
                is_default = 0,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND user_api_key_id = #{keyId} AND deleted_at IS NULL
            """)
    int softDeleteUserKey(@Param("userId") Long userId, @Param("keyId") Long keyId);

    @Update("""
            UPDATE model_official_api_keys
            SET deleted_at = CURRENT_TIMESTAMP(3),
                is_default = 0,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE official_api_key_id = #{keyId} AND deleted_at IS NULL
            """)
    int softDeleteOfficialKey(@Param("keyId") Long keyId);

    @Select("""
            SELECT id, project_policy_id, project_id, policy_name, scene, provider_model_id, model_name, user_key_id,
                   base_url, official_key_id,
                   temperature, top_p, max_tokens,
                   CAST(fallback_policy_json AS CHAR) AS fallback_policy_json,
                   is_default, created_at, updated_at, deleted_at
            FROM model_project_policies
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY is_default DESC, id DESC
            """)
    List<ModelProjectPolicy> listProjectPolicies(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, project_policy_id, project_id, policy_name, scene, provider_model_id, model_name, user_key_id,
                   base_url, official_key_id,
                   temperature, top_p, max_tokens,
                   CAST(fallback_policy_json AS CHAR) AS fallback_policy_json,
                   is_default, created_at, updated_at, deleted_at
            FROM model_project_policies
            WHERE project_id = #{projectId} AND project_policy_id = #{policyId} AND deleted_at IS NULL
            LIMIT 1
            """)
    ModelProjectPolicy findProjectPolicy(@Param("projectId") Long projectId,
                                         @Param("policyId") Long policyId);

    @Select("""
            SELECT id, project_policy_id, project_id, policy_name, scene, provider_model_id, model_name, user_key_id,
                   base_url, official_key_id,
                   temperature, top_p, max_tokens,
                   CAST(fallback_policy_json AS CHAR) AS fallback_policy_json,
                   is_default, created_at, updated_at, deleted_at
            FROM model_project_policies
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY is_default DESC, id DESC
            LIMIT 1
            """)
    ModelProjectPolicy findDefaultProjectPolicy(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, provider_id, code, name, base_url, auth_type, status, created_at, updated_at
            FROM model_providers
            WHERE provider_id = #{providerId}
            LIMIT 1
            """)
    ModelProvider findProvider(@Param("providerId") Long providerId);

    @Select("""
            SELECT id, user_api_key_id, user_id, provider_id, key_name, encrypted_api_key, masked_api_key,
                   is_default, last_used_at, status, created_at, updated_at, deleted_at
            FROM model_user_api_keys
            WHERE user_api_key_id = #{userKeyId} AND deleted_at IS NULL
            LIMIT 1
            """)
    ModelUserApiKey findUserKey(@Param("userKeyId") Long userKeyId);

    @Select("""
            SELECT id, official_api_key_id, provider_id, key_name, encrypted_api_key, masked_api_key,
                   is_default, last_used_at, status, created_at, updated_at, deleted_at
            FROM model_official_api_keys
            WHERE official_api_key_id = #{officialKeyId} AND deleted_at IS NULL
            LIMIT 1
            """)
    ModelOfficialApiKey findOfficialKey(@Param("officialKeyId") Long officialKeyId);

    @Select("""
            SELECT id, official_api_key_id, provider_id, key_name, encrypted_api_key, masked_api_key,
                   is_default, last_used_at, status, created_at, updated_at, deleted_at
            FROM model_official_api_keys
            WHERE provider_id = #{providerId} AND is_default = 1 AND deleted_at IS NULL
            ORDER BY id DESC
            LIMIT 1
            """)
    ModelOfficialApiKey findDefaultOfficialKey(@Param("providerId") Long providerId);

    @Insert("""
            INSERT INTO model_project_policies(project_policy_id, project_id, policy_name, scene, provider_model_id, model_name, base_url, user_key_id, official_key_id,
                                               temperature, top_p, max_tokens, fallback_policy_json, is_default)
            VALUES (#{projectPolicyId}, #{projectId}, #{policyName}, #{scene}, #{providerModelId}, #{modelName}, #{baseUrl}, #{userKeyId}, #{officialKeyId},
                    #{temperature}, #{topP}, #{maxTokens}, #{fallbackPolicyJson}, #{isDefault})
            """)
    int insertPolicy(@Param("projectPolicyId") Long projectPolicyId,
                     @Param("projectId") Long projectId,
                     @Param("policyName") String policyName,
                     @Param("scene") String scene,
                     @Param("providerModelId") Long providerModelId,
                     @Param("modelName") String modelName,
                     @Param("baseUrl") String baseUrl,
                     @Param("userKeyId") Long userKeyId,
                     @Param("officialKeyId") Long officialKeyId,
                     @Param("temperature") BigDecimal temperature,
                     @Param("topP") BigDecimal topP,
                     @Param("maxTokens") Integer maxTokens,
                     @Param("fallbackPolicyJson") String fallbackPolicyJson,
                     @Param("isDefault") boolean isDefault);

    @Update("""
            UPDATE model_project_policies
            SET policy_name = COALESCE(#{policyName}, policy_name),
                scene = COALESCE(#{scene}, scene),
                provider_model_id = COALESCE(#{providerModelId}, provider_model_id),
                model_name = COALESCE(#{modelName}, model_name),
                base_url = COALESCE(#{baseUrl}, base_url),
                user_key_id = COALESCE(#{userKeyId}, user_key_id),
                official_key_id = COALESCE(#{officialKeyId}, official_key_id),
                temperature = COALESCE(#{temperature}, temperature),
                top_p = COALESCE(#{topP}, top_p),
                max_tokens = COALESCE(#{maxTokens}, max_tokens),
                fallback_policy_json = COALESCE(#{fallbackPolicyJson}, fallback_policy_json),
                is_default = COALESCE(#{isDefault}, is_default),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND project_policy_id = #{policyId} AND deleted_at IS NULL
            """)
    int updatePolicy(@Param("projectId") Long projectId,
                     @Param("policyId") Long policyId,
                     @Param("policyName") String policyName,
                     @Param("scene") String scene,
                     @Param("providerModelId") Long providerModelId,
                     @Param("modelName") String modelName,
                     @Param("baseUrl") String baseUrl,
                     @Param("userKeyId") Long userKeyId,
                     @Param("officialKeyId") Long officialKeyId,
                     @Param("temperature") BigDecimal temperature,
                     @Param("topP") BigDecimal topP,
                     @Param("maxTokens") Integer maxTokens,
                     @Param("fallbackPolicyJson") String fallbackPolicyJson,
                     @Param("isDefault") Boolean isDefault);

    @Update("""
            UPDATE model_project_policies
            SET deleted_at = CURRENT_TIMESTAMP(3),
                is_default = 0,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND project_policy_id = #{policyId} AND deleted_at IS NULL
            """)
    int softDeletePolicy(@Param("projectId") Long projectId, @Param("policyId") Long policyId);

    @Update("""
            UPDATE model_project_policies
            SET is_default = 0,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    int clearDefaultPolicy(@Param("projectId") Long projectId);

    @Update("""
            UPDATE model_project_policies
            SET is_default = 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND project_policy_id = #{policyId} AND deleted_at IS NULL
            """)
    int setDefaultPolicy(@Param("projectId") Long projectId, @Param("policyId") Long policyId);

    @Select("""
            SELECT model_config_id AS modelConfigId,
                   model_name AS modelName,
                   provider_id AS providerId,
                   key_source_type AS keySourceType,
                   base_url AS baseUrl,
                   user_key_id AS userKeyId,
                   official_key_id AS officialKeyId
            FROM model_user_configurations
            WHERE user_id = #{userId} AND deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<Map<String, Object>> listUserModelConfigs(@Param("userId") Long userId);

    @Update("""
            UPDATE iam_users
            SET main_agent_model_config_id = #{mainAgentModelConfigId},
                dirty_work_agent_model_config_id = #{dirtyWorkAgentModelConfigId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateUserModelPreferences(@Param("userId") Long userId,
                                   @Param("mainAgentModelConfigId") Long mainAgentModelConfigId,
                                   @Param("dirtyWorkAgentModelConfigId") Long dirtyWorkAgentModelConfigId);

    @Select("""
            SELECT COUNT(1)
            FROM model_user_configurations
            WHERE user_id = #{userId}
              AND model_config_id = #{modelConfigId}
              AND deleted_at IS NULL
              AND (
                    (key_source_type = 'USER_KEY' AND user_key_id IS NOT NULL)
                    OR (key_source_type = 'OFFICIAL_KEY' AND official_key_id IS NOT NULL)
                  )
            """)
    int countUsableModelConfig(@Param("userId") Long userId,
                               @Param("modelConfigId") Long modelConfigId);
}
