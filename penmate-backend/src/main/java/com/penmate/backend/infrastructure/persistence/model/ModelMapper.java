package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderCapability;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ModelMapper {

    String CONFIG_SELECT = """
            SELECT mc.model_config_id, mc.scope_type, mc.owner_user_id, mc.provider_id,
                   p.code AS provider_code, p.name AS provider_name, p.base_url AS provider_base_url,
                   p.auth_type AS provider_auth_type, cap.protocol_code,
                   mc.display_name, mc.model_type, mc.model_name, mc.base_url, mc.distance_metric,
                   mc.context_window_turns, mc.max_context_tokens,
                   COALESCE(uak.masked_api_key, oak.masked_api_key) AS masked_api_key,
                   COALESCE(uak.status, oak.status,
                            CASE WHEN p.auth_type = 'NONE' THEN 'ACTIVE' ELSE NULL END) AS credential_status,
                   mc.status, mc.created_by, mc.updated_by, mc.created_at, mc.updated_at
            FROM model_configurations mc
            JOIN model_providers p ON p.provider_id = mc.provider_id AND p.deleted_at IS NULL
            JOIN model_provider_capabilities cap
              ON cap.provider_id = mc.provider_id
             AND cap.capability_code = mc.model_type
             AND cap.status = 'ACTIVE' AND cap.deleted_at IS NULL
            LEFT JOIN model_user_api_keys uak
              ON uak.model_config_id = mc.model_config_id AND uak.deleted_at IS NULL
            LEFT JOIN model_official_api_keys oak
              ON oak.model_config_id = mc.model_config_id AND oak.deleted_at IS NULL
            """;

    @Select("""
            SELECT id, provider_id, code, name, base_url, auth_type, status, created_at, updated_at
            FROM model_providers
            WHERE deleted_at IS NULL AND status = 'ACTIVE'
            ORDER BY name, provider_id
            """)
    List<ModelProvider> listProviders();

    @Select("""
            SELECT id, provider_id, code, name, base_url, auth_type, status, created_at, updated_at
            FROM model_providers
            WHERE provider_id = #{providerId} AND deleted_at IS NULL
            """)
    ModelProvider findProvider(Long providerId);

    @Select("""
            SELECT provider_capability_id, provider_id, capability_code, protocol_code, status
            FROM model_provider_capabilities
            WHERE provider_id = #{providerId} AND deleted_at IS NULL AND status = 'ACTIVE'
            ORDER BY capability_code
            """)
    List<ModelProviderCapability> listCapabilities(Long providerId);

    @Select("""
            SELECT provider_capability_id, provider_id, capability_code, protocol_code, status
            FROM model_provider_capabilities
            WHERE provider_id = #{providerId} AND capability_code = #{capabilityCode}
              AND deleted_at IS NULL AND status = 'ACTIVE'
            """)
    ModelProviderCapability findCapability(@Param("providerId") Long providerId,
                                           @Param("capabilityCode") String capabilityCode);

    @Select(CONFIG_SELECT + """
            WHERE mc.deleted_at IS NULL
              AND (mc.scope_type = 'SYSTEM' OR (mc.scope_type = 'USER' AND mc.owner_user_id = #{userId}))
            ORDER BY mc.model_type, mc.scope_type, mc.display_name, mc.model_config_id
            """)
    List<ModelConfiguration> listAccessibleConfigurations(Long userId);

    @Select(CONFIG_SELECT + """
            WHERE mc.model_config_id = #{modelConfigId} AND mc.deleted_at IS NULL
              AND (mc.scope_type = 'SYSTEM' OR (mc.scope_type = 'USER' AND mc.owner_user_id = #{userId}))
            """)
    ModelConfiguration findAccessibleConfiguration(@Param("userId") Long userId,
                                                   @Param("modelConfigId") Long modelConfigId);

    @Select(CONFIG_SELECT + """
            WHERE mc.model_config_id = #{modelConfigId} AND mc.scope_type = 'USER'
              AND mc.owner_user_id = #{userId} AND mc.deleted_at IS NULL
            FOR UPDATE OF mc
            """)
    ModelConfiguration findUserConfigurationForUpdate(@Param("userId") Long userId,
                                                      @Param("modelConfigId") Long modelConfigId);

    @Select(CONFIG_SELECT + """
            WHERE mc.model_config_id = #{modelConfigId} AND mc.scope_type = 'SYSTEM'
              AND mc.deleted_at IS NULL
            FOR UPDATE OF mc
            """)
    ModelConfiguration findSystemConfigurationForUpdate(Long modelConfigId);

    @Select("""
            SELECT user_api_key_id AS credential_id, model_config_id, user_id AS owner_user_id,
                   provider_id, encrypted_api_key, masked_api_key, status
            FROM model_user_api_keys
            WHERE model_config_id = #{modelConfigId} AND user_id = #{ownerUserId} AND deleted_at IS NULL
            """)
    ModelCredential findUserCredential(@Param("ownerUserId") Long ownerUserId,
                                       @Param("modelConfigId") Long modelConfigId);

    @Select("""
            SELECT official_api_key_id AS credential_id, model_config_id, NULL::BIGINT AS owner_user_id,
                   provider_id, encrypted_api_key, masked_api_key, status
            FROM model_official_api_keys
            WHERE model_config_id = #{modelConfigId} AND deleted_at IS NULL
            """)
    ModelCredential findOfficialCredential(Long modelConfigId);

    @Insert("""
            INSERT INTO model_configurations(
                model_config_id, scope_type, owner_user_id, provider_id, display_name,
                model_type, model_name, base_url, distance_metric, context_window_turns,
                max_context_tokens, status, created_by, updated_by
            ) VALUES (
                #{modelConfigId}, #{scopeType}, #{ownerUserId}, #{providerId}, #{displayName},
                #{modelType}, #{modelName}, #{baseUrl}, #{distanceMetric}, #{contextWindowTurns},
                #{maxContextTokens}, #{status}, #{createdBy}, #{updatedBy}
            )
            """)
    int insertConfiguration(ModelConfiguration configuration);

    @Update("""
            UPDATE model_configurations
            SET provider_id = #{providerId}, display_name = #{displayName}, model_name = #{modelName},
                base_url = #{baseUrl}, distance_metric = #{distanceMetric},
                context_window_turns = #{contextWindowTurns}, max_context_tokens = #{maxContextTokens},
                status = #{status}, updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE model_config_id = #{modelConfigId} AND scope_type = #{scopeType}
              AND (#{scopeType} = 'SYSTEM' OR owner_user_id = #{ownerUserId}) AND deleted_at IS NULL
            """)
    int updateConfiguration(ModelConfiguration configuration);

    @Insert("""
            INSERT INTO model_user_api_keys(
                user_api_key_id, model_config_id, user_id, provider_id,
                encrypted_api_key, masked_api_key, status
            ) VALUES (
                #{credential.credentialId}, #{configuration.modelConfigId}, #{configuration.ownerUserId},
                #{configuration.providerId}, #{credential.encryptedApiKey}, #{credential.maskedApiKey}, #{credential.status}
            )
            """)
    int insertUserCredential(@Param("configuration") ModelConfiguration configuration,
                             @Param("credential") ModelCredential credential);

    @Insert("""
            INSERT INTO model_official_api_keys(
                official_api_key_id, model_config_id, provider_id,
                encrypted_api_key, masked_api_key, status
            ) VALUES (
                #{credential.credentialId}, #{configuration.modelConfigId}, #{configuration.providerId},
                #{credential.encryptedApiKey}, #{credential.maskedApiKey}, #{credential.status}
            )
            """)
    int insertOfficialCredential(@Param("configuration") ModelConfiguration configuration,
                                 @Param("credential") ModelCredential credential);

    @Update("""
            UPDATE model_user_api_keys
            SET provider_id = #{configuration.providerId}, encrypted_api_key = #{credential.encryptedApiKey},
                masked_api_key = #{credential.maskedApiKey}, status = #{credential.status},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE model_config_id = #{configuration.modelConfigId}
              AND user_id = #{configuration.ownerUserId} AND deleted_at IS NULL
            """)
    int updateUserCredential(@Param("configuration") ModelConfiguration configuration,
                             @Param("credential") ModelCredential credential);

    @Update("""
            UPDATE model_official_api_keys
            SET provider_id = #{configuration.providerId}, encrypted_api_key = #{credential.encryptedApiKey},
                masked_api_key = #{credential.maskedApiKey}, status = #{credential.status},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE model_config_id = #{configuration.modelConfigId} AND deleted_at IS NULL
            """)
    int updateOfficialCredential(@Param("configuration") ModelConfiguration configuration,
                                 @Param("credential") ModelCredential credential);

    @Update("""
            UPDATE model_configurations
            SET deleted_at = CURRENT_TIMESTAMP(3), updated_by = #{actorUserId}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE model_config_id = #{configuration.modelConfigId} AND scope_type = #{configuration.scopeType}
              AND (#{configuration.scopeType} = 'SYSTEM' OR owner_user_id = #{configuration.ownerUserId})
              AND deleted_at IS NULL
            """)
    int softDeleteConfiguration(@Param("configuration") ModelConfiguration configuration,
                                @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE model_user_api_keys
            SET deleted_at = CURRENT_TIMESTAMP(3), status = 'DELETED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE model_config_id = #{modelConfigId} AND deleted_at IS NULL
            """)
    int softDeleteUserCredential(Long modelConfigId);

    @Update("""
            UPDATE model_official_api_keys
            SET deleted_at = CURRENT_TIMESTAMP(3), status = 'DELETED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE model_config_id = #{modelConfigId} AND deleted_at IS NULL
            """)
    int softDeleteOfficialCredential(Long modelConfigId);

    @Select("""
            SELECT COUNT(*)
            FROM agent_run_model_bindings b
            JOIN agent_runs r ON r.run_id = b.run_id
            WHERE b.model_config_id = #{modelConfigId}
              AND r.run_status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'SUSPENDED')
            """)
    int countNonterminalRunReferences(Long modelConfigId);

    @Select("""
            SELECT project_id
            FROM project_ai_configurations
            WHERE embedding_model_config_id = #{modelConfigId}
               OR router_model_config_id = #{modelConfigId}
            ORDER BY project_id
            """)
    List<Long> listDependentProjectIds(Long modelConfigId);

    @Select("""
            SELECT project_id
            FROM project_ai_configurations
            WHERE embedding_model_config_id = #{modelConfigId}
               OR router_model_config_id = #{modelConfigId}
            ORDER BY project_id
            FOR UPDATE
            """)
    List<Long> lockDependentProjectIds(Long modelConfigId);

    @Update("""
            UPDATE project_ai_configurations
            SET story_bible_routing_mode = 'LLM_SELECTOR', index_status = 'REINDEX_REQUIRED',
                active_index_build_id = NULL, last_error_code = 'EMBEDDING_CONFIG_CHANGED',
                last_error_message = #{reason}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE embedding_model_config_id = #{modelConfigId}
            """)
    int markDependentProjectsReindexRequired(@Param("modelConfigId") Long modelConfigId,
                                             @Param("reason") String reason);

    @Update("""
            UPDATE project_ai_configurations
            SET embedding_model_config_id = CASE WHEN embedding_model_config_id = #{modelConfigId} THEN NULL ELSE embedding_model_config_id END,
                router_model_config_id = CASE WHEN router_model_config_id = #{modelConfigId} THEN NULL ELSE router_model_config_id END,
                story_bible_routing_mode = 'LLM_SELECTOR', index_status = 'UNBOUND',
                active_index_build_id = NULL, last_error_code = NULL, last_error_message = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE embedding_model_config_id = #{modelConfigId}
               OR router_model_config_id = #{modelConfigId}
            """)
    int unbindDependentProjects(Long modelConfigId);

    @Update("""
            UPDATE model_user_preferences
            SET default_main_chat_model_config_id = CASE WHEN default_main_chat_model_config_id = #{modelConfigId} THEN NULL ELSE default_main_chat_model_config_id END,
                default_worker_chat_model_config_id = CASE WHEN default_worker_chat_model_config_id = #{modelConfigId} THEN NULL ELSE default_worker_chat_model_config_id END,
                default_embedding_model_config_id = CASE WHEN default_embedding_model_config_id = #{modelConfigId} THEN NULL ELSE default_embedding_model_config_id END,
                default_router_model_config_id = CASE WHEN default_router_model_config_id = #{modelConfigId} THEN NULL ELSE default_router_model_config_id END,
                default_story_bible_routing_mode = CASE WHEN default_embedding_model_config_id = #{modelConfigId} THEN 'LLM_SELECTOR' ELSE default_story_bible_routing_mode END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE default_main_chat_model_config_id = #{modelConfigId}
               OR default_worker_chat_model_config_id = #{modelConfigId}
               OR default_embedding_model_config_id = #{modelConfigId}
               OR default_router_model_config_id = #{modelConfigId}
            """)
    int clearUserDefaultReferences(Long modelConfigId);

    @Select("""
            SELECT (
                (SELECT COUNT(*) FROM project_ai_configurations
                 WHERE embedding_model_config_id = #{modelConfigId} OR router_model_config_id = #{modelConfigId})
                +
                (SELECT COUNT(*) FROM model_user_preferences
                 WHERE default_main_chat_model_config_id = #{modelConfigId}
                    OR default_worker_chat_model_config_id = #{modelConfigId}
                    OR default_embedding_model_config_id = #{modelConfigId}
                    OR default_router_model_config_id = #{modelConfigId})
                +
                (SELECT COUNT(*) FROM agent_run_model_bindings WHERE model_config_id = #{modelConfigId})
            )
            """)
    int countAllReferences(Long modelConfigId);

    @Select("""
            SELECT user_id, default_main_chat_model_config_id, default_worker_chat_model_config_id,
                   default_embedding_model_config_id, default_router_model_config_id,
                   default_story_bible_routing_mode, default_chunk_target_characters,
                   default_chunk_overlap_characters, default_chunk_max_characters
            FROM model_user_preferences WHERE user_id = #{userId}
            """)
    ModelUserPreferences findUserPreferences(Long userId);

    @Insert("""
            INSERT INTO model_user_preferences(
                user_id, default_main_chat_model_config_id, default_worker_chat_model_config_id,
                default_embedding_model_config_id, default_router_model_config_id,
                default_story_bible_routing_mode, default_chunk_target_characters,
                default_chunk_overlap_characters, default_chunk_max_characters
            ) VALUES (
                #{userId}, #{defaultMainChatModelConfigId}, #{defaultWorkerChatModelConfigId},
                #{defaultEmbeddingModelConfigId}, #{defaultRouterModelConfigId},
                #{defaultStoryBibleRoutingMode}, #{defaultChunkTargetCharacters},
                #{defaultChunkOverlapCharacters}, #{defaultChunkMaxCharacters}
            )
            ON CONFLICT (user_id) DO UPDATE SET
                default_main_chat_model_config_id = EXCLUDED.default_main_chat_model_config_id,
                default_worker_chat_model_config_id = EXCLUDED.default_worker_chat_model_config_id,
                default_embedding_model_config_id = EXCLUDED.default_embedding_model_config_id,
                default_router_model_config_id = EXCLUDED.default_router_model_config_id,
                default_story_bible_routing_mode = EXCLUDED.default_story_bible_routing_mode,
                default_chunk_target_characters = EXCLUDED.default_chunk_target_characters,
                default_chunk_overlap_characters = EXCLUDED.default_chunk_overlap_characters,
                default_chunk_max_characters = EXCLUDED.default_chunk_max_characters,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertUserPreferences(ModelUserPreferences preferences);

    @Select("""
            SELECT COUNT(*)
            FROM model_configurations mc
            WHERE mc.model_config_id = #{modelConfigId} AND mc.model_type = #{modelType}
              AND mc.status = 'ACTIVE' AND mc.deleted_at IS NULL
              AND (mc.scope_type = 'SYSTEM' OR (mc.scope_type = 'USER' AND mc.owner_user_id = #{userId}))
            """)
    int countAccessibleActiveConfiguration(@Param("userId") Long userId,
                                           @Param("modelConfigId") Long modelConfigId,
                                           @Param("modelType") String modelType);
}
