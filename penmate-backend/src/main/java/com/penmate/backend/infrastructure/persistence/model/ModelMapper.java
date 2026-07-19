package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
            SET is_default = FALSE
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    int clearDefaultUserKey(@Param("userId") Long userId);

    @Update("""
            UPDATE model_official_api_keys
            SET is_default = FALSE,
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
                is_default = FALSE,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND user_api_key_id = #{keyId} AND deleted_at IS NULL
            """)
    int softDeleteUserKey(@Param("userId") Long userId, @Param("keyId") Long keyId);

    @Update("""
            UPDATE model_official_api_keys
            SET deleted_at = CURRENT_TIMESTAMP(3),
                is_default = FALSE,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE official_api_key_id = #{keyId} AND deleted_at IS NULL
            """)
    int softDeleteOfficialKey(@Param("keyId") Long keyId);

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
            WHERE provider_id = #{providerId} AND is_default = TRUE AND deleted_at IS NULL
            ORDER BY id DESC
            LIMIT 1
            """)
    ModelOfficialApiKey findDefaultOfficialKey(@Param("providerId") Long providerId);

    @Select("""
            SELECT muc.model_config_id AS modelConfigId,
                   muc.user_id AS userId,
                   muc.provider_id AS providerId,
                   muc.model_name AS modelName,
                   muc.base_url AS baseUrl,
                   muc.key_source_type AS keySourceType,
                   muc.user_key_id AS userKeyId,
                   muc.official_key_id AS officialKeyId,
                   muc.context_window_turns AS contextWindowTurns,
                   muc.max_context_tokens AS maxContextTokens,
                   CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.key_name ELSE mok.key_name END AS keyName,
                   CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.masked_api_key ELSE mok.masked_api_key END AS maskedApiKey,
                   muc.status AS status,
                   muc.updated_at AS updatedAt
            FROM model_user_configurations muc
            LEFT JOIN model_user_api_keys muk
                   ON muc.user_key_id = muk.user_api_key_id
                  AND muk.deleted_at IS NULL
            LEFT JOIN model_official_api_keys mok
                   ON muc.official_key_id = mok.official_api_key_id
                  AND mok.deleted_at IS NULL
            WHERE muc.user_id = #{userId}
              AND muc.deleted_at IS NULL
            ORDER BY muc.id DESC
            """)
    List<Map<String, Object>> listUserModelConfigs(@Param("userId") Long userId);

    @Select("""
            SELECT muc.model_config_id AS modelConfigId,
                   muc.user_id AS userId,
                   muc.provider_id AS providerId,
                   muc.model_name AS modelName,
                   muc.base_url AS baseUrl,
                   muc.key_source_type AS keySourceType,
                   muc.user_key_id AS userKeyId,
                   muc.official_key_id AS officialKeyId,
                   muc.context_window_turns AS contextWindowTurns,
                   muc.max_context_tokens AS maxContextTokens,
                   CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.key_name ELSE mok.key_name END AS keyName,
                   CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.encrypted_api_key ELSE mok.encrypted_api_key END AS encryptedApiKey,
                   CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.masked_api_key ELSE mok.masked_api_key END AS maskedApiKey,
                   CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.status ELSE mok.status END AS keyStatus,
                   muc.status AS status
            FROM model_user_configurations muc
            LEFT JOIN model_user_api_keys muk
                   ON muc.user_key_id = muk.user_api_key_id
                  AND muk.deleted_at IS NULL
            LEFT JOIN model_official_api_keys mok
                   ON muc.official_key_id = mok.official_api_key_id
                  AND mok.deleted_at IS NULL
            WHERE muc.user_id = #{userId}
              AND muc.model_config_id = #{modelConfigId}
              AND muc.deleted_at IS NULL
            LIMIT 1
            """)
    Map<String, Object> findUserModelConfig(@Param("userId") Long userId,
                                            @Param("modelConfigId") Long modelConfigId);

    @Insert("""
            INSERT INTO model_user_configurations(
                model_config_id, user_id, provider_id, model_name, base_url,
                key_source_type, user_key_id, official_key_id, context_window_turns, max_context_tokens, status
            )
            VALUES (
                #{modelConfigId}, #{userId}, #{providerId}, #{modelName}, #{baseUrl},
                #{keySourceType}, #{userKeyId}, #{officialKeyId}, #{contextWindowTurns}, #{maxContextTokens}, #{status}
            )
            """)
    int insertUserModelConfig(@Param("modelConfigId") Long modelConfigId,
                              @Param("userId") Long userId,
                              @Param("providerId") Long providerId,
                              @Param("modelName") String modelName,
                              @Param("baseUrl") String baseUrl,
                              @Param("keySourceType") String keySourceType,
                              @Param("userKeyId") Long userKeyId,
                              @Param("officialKeyId") Long officialKeyId,
                              @Param("contextWindowTurns") Integer contextWindowTurns,
                              @Param("maxContextTokens") Integer maxContextTokens,
                              @Param("status") String status);

    @Update("""
            UPDATE model_user_configurations
            SET provider_id = COALESCE(#{providerId}, provider_id),
                model_name = COALESCE(#{modelName}, model_name),
                base_url = COALESCE(#{baseUrl}, base_url),
                key_source_type = COALESCE(#{keySourceType}, key_source_type),
                user_key_id = #{userKeyId},
                official_key_id = #{officialKeyId},
                context_window_turns = COALESCE(#{contextWindowTurns}, context_window_turns),
                max_context_tokens = COALESCE(#{maxContextTokens}, max_context_tokens),
                status = COALESCE(#{status}, status),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND model_config_id = #{modelConfigId} AND deleted_at IS NULL
            """)
    int updateUserModelConfig(@Param("userId") Long userId,
                              @Param("modelConfigId") Long modelConfigId,
                              @Param("providerId") Long providerId,
                              @Param("modelName") String modelName,
                              @Param("baseUrl") String baseUrl,
                              @Param("keySourceType") String keySourceType,
                              @Param("userKeyId") Long userKeyId,
                              @Param("officialKeyId") Long officialKeyId,
                              @Param("contextWindowTurns") Integer contextWindowTurns,
                              @Param("maxContextTokens") Integer maxContextTokens,
                              @Param("status") String status);

    @Update("""
            UPDATE model_user_configurations
            SET deleted_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND model_config_id = #{modelConfigId} AND deleted_at IS NULL
            """)
    int softDeleteUserModelConfig(@Param("userId") Long userId,
                                  @Param("modelConfigId") Long modelConfigId);

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
            FROM model_user_configurations muc
            LEFT JOIN model_user_api_keys muk
                   ON muc.user_key_id = muk.user_api_key_id
                  AND muk.deleted_at IS NULL
            LEFT JOIN model_official_api_keys mok
                   ON muc.official_key_id = mok.official_api_key_id
                  AND mok.deleted_at IS NULL
            WHERE muc.user_id = #{userId}
              AND muc.model_config_id = #{modelConfigId}
              AND muc.deleted_at IS NULL
              AND muc.status = 'active'
              AND (
                  (muc.key_source_type = 'USER_KEY' AND muk.status = 'active' AND muk.encrypted_api_key IS NOT NULL AND muk.encrypted_api_key <> '')
                  OR (muc.key_source_type = 'OFFICIAL_KEY' AND mok.status = 'active' AND mok.encrypted_api_key IS NOT NULL AND mok.encrypted_api_key <> '')
              )
            """)
    int countUsableModelConfig(@Param("userId") Long userId,
                               @Param("modelConfigId") Long modelConfigId);
}
