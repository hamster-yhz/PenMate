package com.penmate.backend.infrastructure.bootstrap;

import com.penmate.backend.application.model.ModelCapabilityCatalogService;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemDataBootstrap implements ApplicationRunner {

    private static final long ADMIN_USER_ID = 1L;
    private static final long ADMIN_ROLE_ID = 1L;
    private static final long CHAT_CONFIG_ID = 1L;
    private static final long EMBEDDING_CONFIG_ID = 2L;
    private static final long CHAT_KEY_ID = 1L;
    private static final long EMBEDDING_KEY_ID = 2L;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecretCryptoService secretCryptoService;
    private final SystemBootstrapProperties properties;
    private final ModelCapabilityCatalogService capabilityCatalog;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validate();
        bootstrapAdmin();
        jdbcTemplate.update("""
                INSERT INTO iam_user_roles(user_id, role_id) VALUES (?, ?)
                ON CONFLICT (user_id, role_id) DO NOTHING
                """, ADMIN_USER_ID, ADMIN_ROLE_ID);

        Long chatConfigId = bootstrapOptionalModel(
                properties.getChat(), "CHAT", CHAT_CONFIG_ID, CHAT_KEY_ID, null);
        Long embeddingConfigId = bootstrapOptionalModel(
                properties.getEmbedding(), "EMBEDDING", EMBEDDING_CONFIG_ID, EMBEDDING_KEY_ID, "COSINE");
        bootstrapAdminPreferences(chatConfigId, embeddingConfigId);
        log.info("system.bootstrap.completed: reconcile={}, adminUserId={}, chat={}, embedding={}",
                properties.isReconcile(), ADMIN_USER_ID, chatConfigId != null, embeddingConfigId != null);
    }

    private void bootstrapAdmin() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_users WHERE user_id = ? AND deleted_at IS NULL",
                Integer.class,
                ADMIN_USER_ID
        );
        String email = properties.getAdmin().getEmail().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(properties.getAdmin().getPassword());
        if (count != null && count > 0) {
            if (properties.isReconcile()) {
                jdbcTemplate.update("""
                        UPDATE iam_users
                        SET email = ?, password_hash = ?, display_name = 'Admin', status = 1,
                            auth_method = 'local', updated_at = CURRENT_TIMESTAMP(3)
                        WHERE user_id = ? AND deleted_at IS NULL
                        """, email, passwordHash, ADMIN_USER_ID);
            }
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO iam_users(user_id, email, password_hash, display_name, bio, status, auth_method)
                VALUES (?, ?, ?, 'Admin', '', 1, 'local')
                """, ADMIN_USER_ID, email, passwordHash);
    }

    private Long bootstrapOptionalModel(SystemBootstrapProperties.ModelGroup group,
                                        String modelType,
                                        long modelConfigId,
                                        long officialKeyId,
                                        String distanceMetric) {
        if (isEmpty(group)) {
            return null;
        }
        long providerId = requireProviderId(group.getProvider(), modelType);
        ModelCapabilityCatalogService.Resolution capacity = capabilityCatalog.resolveForSave(
                group.getProvider(), group.getModelName(), null, null);
        Integer configCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_configurations WHERE model_config_id = ? AND deleted_at IS NULL",
                Integer.class,
                modelConfigId
        );
        if (configCount == null || configCount == 0) {
            jdbcTemplate.update("""
                    INSERT INTO model_configurations(
                        model_config_id, scope_type, owner_user_id, provider_id, display_name,
                        model_type, model_name, base_url, distance_metric, context_window_turns,
                        max_context_tokens, max_output_tokens, context_capacity_source,
                        context_capacity_source_url, context_capacity_verified_at,
                        status, created_by, updated_by
                    ) VALUES (?, 'SYSTEM', NULL, ?, ?, ?, ?, ?, ?, 6, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """, modelConfigId, providerId, bootstrapDisplayName(modelType, group.getModelName()),
                    modelType, group.getModelName().trim(), group.getBaseUrl().trim(), distanceMetric,
                    capacity.maxContextTokens(), capacity.maxOutputTokens(), capacity.source(),
                    capacity.sourceUrl(), timestamp(capacity),
                    ADMIN_USER_ID, ADMIN_USER_ID);
        } else if (properties.isReconcile()) {
            jdbcTemplate.update("""
                    UPDATE model_configurations
                    SET provider_id = ?, display_name = ?, model_name = ?, base_url = ?,
                        distance_metric = ?, max_context_tokens = ?, max_output_tokens = ?,
                        context_capacity_source = ?, context_capacity_source_url = ?,
                        context_capacity_verified_at = ?, status = 'ACTIVE', updated_by = ?,
                        updated_at = CURRENT_TIMESTAMP(3)
                    WHERE model_config_id = ? AND scope_type = 'SYSTEM' AND deleted_at IS NULL
                    """, providerId, bootstrapDisplayName(modelType, group.getModelName()),
                    group.getModelName().trim(), group.getBaseUrl().trim(), distanceMetric,
                    capacity.maxContextTokens(), capacity.maxOutputTokens(), capacity.source(),
                    capacity.sourceUrl(), timestamp(capacity),
                    ADMIN_USER_ID, modelConfigId);
        }

        String encryptedApiKey = secretCryptoService.encrypt(group.getApiKey());
        String maskedApiKey = mask(group.getApiKey());
        Integer keyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_official_api_keys WHERE model_config_id = ? AND deleted_at IS NULL",
                Integer.class,
                modelConfigId
        );
        if (keyCount == null || keyCount == 0) {
            jdbcTemplate.update("""
                    INSERT INTO model_official_api_keys(
                        official_api_key_id, model_config_id, provider_id, encrypted_api_key,
                        masked_api_key, status
                    ) VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                    """, officialKeyId, modelConfigId, providerId, encryptedApiKey, maskedApiKey);
        } else if (properties.isReconcile()) {
            jdbcTemplate.update("""
                    UPDATE model_official_api_keys
                    SET provider_id = ?, encrypted_api_key = ?, masked_api_key = ?,
                        status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP(3)
                    WHERE model_config_id = ? AND deleted_at IS NULL
                    """, providerId, encryptedApiKey, maskedApiKey, modelConfigId);
        }
        return modelConfigId;
    }

    private void bootstrapAdminPreferences(Long chatConfigId, Long embeddingConfigId) {
        jdbcTemplate.update("""
                INSERT INTO model_user_preferences(
                    user_id, default_creative_model_config_id, default_context_selector_model_config_id,
                    default_embedding_model_config_id, default_story_bible_routing_mode
                ) VALUES (?, ?, ?, ?, 'AGENT_DRIVEN')
                ON CONFLICT (user_id) DO NOTHING
                """, ADMIN_USER_ID, chatConfigId, chatConfigId, embeddingConfigId);
        if (properties.isReconcile()) {
            jdbcTemplate.update("""
                    UPDATE model_user_preferences
                    SET default_creative_model_config_id = ?, default_context_selector_model_config_id = ?,
                        default_embedding_model_config_id = ?, updated_at = CURRENT_TIMESTAMP(3)
                    WHERE user_id = ?
                    """, chatConfigId, chatConfigId, embeddingConfigId, ADMIN_USER_ID);
        }
    }

    private long requireProviderId(String providerCode, String capability) {
        Long providerId = jdbcTemplate.query("""
                        SELECT p.provider_id
                        FROM model_providers p
                        JOIN model_provider_capabilities c ON c.provider_id = p.provider_id
                        WHERE lower(p.code) = lower(?)
                          AND p.status = 'ACTIVE' AND p.deleted_at IS NULL
                          AND c.capability_code = ? AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
                        """,
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                providerCode.trim(), capability);
        if (providerId == null) {
            throw new IllegalStateException(
                    "Unsupported bootstrap " + capability.toLowerCase(Locale.ROOT) + " provider: " + providerCode);
        }
        return providerId;
    }

    private void validate() {
        requireText(properties.getAdmin().getEmail(), "BOOTSTRAP_ADMIN_EMAIL");
        requireText(properties.getAdmin().getPassword(), "BOOTSTRAP_ADMIN_PASSWORD");
        validateOptionalGroup(properties.getChat(), "BOOTSTRAP_CHAT");
        validateOptionalGroup(properties.getEmbedding(), "BOOTSTRAP_EMBEDDING");
    }

    private void validateOptionalGroup(SystemBootstrapProperties.ModelGroup group, String prefix) {
        if (isEmpty(group)) {
            return;
        }
        requireText(group.getProvider(), prefix + "_PROVIDER");
        requireText(group.getBaseUrl(), prefix + "_BASE_URL");
        requireText(group.getApiKey(), prefix + "_API_KEY");
        requireText(group.getModelName(), prefix + "_MODEL_NAME");
    }

    private boolean isEmpty(SystemBootstrapProperties.ModelGroup group) {
        return isBlank(group.getProvider())
                && isBlank(group.getBaseUrl())
                && isBlank(group.getApiKey())
                && isBlank(group.getModelName());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requireText(String value, String environmentName) {
        if (isBlank(value)) {
            throw new IllegalStateException(environmentName + " is required when its bootstrap group is configured");
        }
    }

    private String bootstrapDisplayName(String modelType, String modelName) {
        return "Bootstrap " + ("CHAT".equals(modelType) ? "Chat" : "Embedding") + " - " + modelName.trim();
    }

    private String mask(String apiKey) {
        String normalized = apiKey.trim();
        int visible = Math.min(4, normalized.length());
        return "****" + normalized.substring(normalized.length() - visible);
    }

    private Timestamp timestamp(ModelCapabilityCatalogService.Resolution capacity) {
        return capacity.verifiedAt() == null ? null : Timestamp.from(capacity.verifiedAt());
    }
}
