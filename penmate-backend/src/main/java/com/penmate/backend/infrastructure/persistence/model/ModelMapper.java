package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderModel;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ModelMapper {

    @Select("""
            SELECT id, code, name, base_url, auth_type, status, created_at, updated_at
            FROM model_providers
            ORDER BY id DESC
            """)
    List<ModelProvider> listProviders();

    @Select("""
            SELECT m.id, m.provider_id, m.model_code, m.model_name, m.context_window,
                   m.max_output, CAST(m.pricing_json AS CHAR) AS pricing_json, m.status, m.created_at
            FROM model_provider_models m
            JOIN model_providers p ON p.id = m.provider_id
            WHERE p.code = #{providerCode}
            ORDER BY m.id DESC
            """)
    List<ModelProviderModel> listProviderModels(@Param("providerCode") String providerCode);

    @Select("""
            SELECT id, user_id, provider_id, key_name, encrypted_api_key, masked_api_key,
                   is_default, last_used_at, status, created_at, updated_at, deleted_at
            FROM model_user_api_keys
            WHERE user_id = #{userId} AND deleted_at IS NULL
            ORDER BY is_default DESC, id DESC
            """)
    List<ModelUserApiKey> listUserKeys(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO model_user_api_keys(user_id, provider_id, key_name, encrypted_api_key, masked_api_key, is_default, status)
            VALUES (#{userId}, #{providerId}, #{keyName}, #{encryptedApiKey}, #{maskedApiKey}, #{isDefault}, #{status})
            """)
    int insertUserKey(@Param("userId") Long userId,
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
            UPDATE model_user_api_keys
            SET key_name = COALESCE(#{keyName}, key_name),
                encrypted_api_key = COALESCE(#{encryptedApiKey}, encrypted_api_key),
                masked_api_key = COALESCE(#{maskedApiKey}, masked_api_key),
                is_default = COALESCE(#{isDefault}, is_default),
                status = COALESCE(#{status}, status),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND id = #{keyId} AND deleted_at IS NULL
            """)
    int updateUserKey(@Param("userId") Long userId,
                      @Param("keyId") Long keyId,
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
            WHERE user_id = #{userId} AND id = #{keyId} AND deleted_at IS NULL
            """)
    int softDeleteUserKey(@Param("userId") Long userId, @Param("keyId") Long keyId);

    @Select("""
            SELECT id, project_id, policy_name, scene, provider_model_id, user_key_id,
                   temperature, top_p, max_tokens,
                   CAST(fallback_policy_json AS CHAR) AS fallback_policy_json,
                   is_default, created_at, updated_at, deleted_at
            FROM model_project_policies
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            ORDER BY is_default DESC, id DESC
            """)
    List<ModelProjectPolicy> listProjectPolicies(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO model_project_policies(project_id, policy_name, scene, provider_model_id, user_key_id,
                                               temperature, top_p, max_tokens, fallback_policy_json, is_default)
            VALUES (#{projectId}, #{policyName}, #{scene}, #{providerModelId}, #{userKeyId},
                    #{temperature}, #{topP}, #{maxTokens}, #{fallbackPolicyJson}, #{isDefault})
            """)
    int insertPolicy(@Param("projectId") Long projectId,
                     @Param("policyName") String policyName,
                     @Param("scene") String scene,
                     @Param("providerModelId") Long providerModelId,
                     @Param("userKeyId") Long userKeyId,
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
                user_key_id = COALESCE(#{userKeyId}, user_key_id),
                temperature = COALESCE(#{temperature}, temperature),
                top_p = COALESCE(#{topP}, top_p),
                max_tokens = COALESCE(#{maxTokens}, max_tokens),
                fallback_policy_json = COALESCE(#{fallbackPolicyJson}, fallback_policy_json),
                is_default = COALESCE(#{isDefault}, is_default),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND id = #{policyId} AND deleted_at IS NULL
            """)
    int updatePolicy(@Param("projectId") Long projectId,
                     @Param("policyId") Long policyId,
                     @Param("policyName") String policyName,
                     @Param("scene") String scene,
                     @Param("providerModelId") Long providerModelId,
                     @Param("userKeyId") Long userKeyId,
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
            WHERE project_id = #{projectId} AND id = #{policyId} AND deleted_at IS NULL
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
            WHERE project_id = #{projectId} AND id = #{policyId} AND deleted_at IS NULL
            """)
    int setDefaultPolicy(@Param("projectId") Long projectId, @Param("policyId") Long policyId);
}

