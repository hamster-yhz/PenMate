package com.penmate.backend.infrastructure.bootstrap;

import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemDataBootstrap implements ApplicationRunner {

    private static final long ADMIN_USER_ID = 1L;
    private static final long ADMIN_ROLE_ID = 1L;
    private static final long OFFICIAL_KEY_ID = 1L;
    private static final long MODEL_CONFIG_ID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecretCryptoService secretCryptoService;
    private final SystemBootstrapProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validate();
        long providerId = requireProviderId(properties.getModel().getProvider());
        bootstrapAdmin();
        bootstrapModel(providerId);
        jdbcTemplate.update("""
                INSERT INTO iam_user_roles(user_id, role_id) VALUES (?, ?)
                ON CONFLICT (user_id, role_id) DO NOTHING
                """, ADMIN_USER_ID, ADMIN_ROLE_ID);
        jdbcTemplate.update("""
                UPDATE iam_users
                SET main_agent_model_config_id = ?, dirty_work_agent_model_config_id = ?,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE user_id = ? AND deleted_at IS NULL
                """, MODEL_CONFIG_ID, MODEL_CONFIG_ID, ADMIN_USER_ID);
        log.info("system.bootstrap.completed: reconcile={}, adminUserId={}, modelConfigId={}",
                properties.isReconcile(), ADMIN_USER_ID, MODEL_CONFIG_ID);
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
                INSERT INTO iam_users(user_id, email, password_hash, display_name, status, auth_method)
                VALUES (?, ?, ?, 'Admin', 1, 'local')
                """, ADMIN_USER_ID, email, passwordHash);
    }

    private void bootstrapModel(long providerId) {
        String encryptedApiKey = secretCryptoService.encrypt(properties.getModel().getApiKey());
        String maskedApiKey = mask(properties.getModel().getApiKey());
        Integer keyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_official_api_keys WHERE official_api_key_id = ? AND deleted_at IS NULL",
                Integer.class,
                OFFICIAL_KEY_ID
        );
        if (keyCount == null || keyCount == 0) {
            jdbcTemplate.update("""
                    INSERT INTO model_official_api_keys(
                        official_api_key_id, provider_id, key_name, encrypted_api_key,
                        masked_api_key, is_default, status
                    ) VALUES (?, ?, 'Bootstrap key', ?, ?, TRUE, 'active')
                    """, OFFICIAL_KEY_ID, providerId, encryptedApiKey, maskedApiKey);
        } else if (properties.isReconcile()) {
            jdbcTemplate.update("""
                    UPDATE model_official_api_keys
                    SET provider_id = ?, encrypted_api_key = ?, masked_api_key = ?,
                        is_default = TRUE, status = 'active', updated_at = CURRENT_TIMESTAMP(3)
                    WHERE official_api_key_id = ? AND deleted_at IS NULL
                    """, providerId, encryptedApiKey, maskedApiKey, OFFICIAL_KEY_ID);
        }

        Integer configCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_user_configurations WHERE model_config_id = ? AND deleted_at IS NULL",
                Integer.class,
                MODEL_CONFIG_ID
        );
        if (configCount == null || configCount == 0) {
            jdbcTemplate.update("""
                    INSERT INTO model_user_configurations(
                        model_config_id, user_id, provider_id, model_name, base_url,
                        key_source_type, official_key_id, context_window_turns,
                        max_context_tokens, status
                    ) VALUES (?, ?, ?, ?, ?, 'OFFICIAL_KEY', ?, 6, 128000, 'active')
                    """, MODEL_CONFIG_ID, ADMIN_USER_ID, providerId,
                    properties.getModel().getModelName().trim(), properties.getModel().getBaseUrl().trim(), OFFICIAL_KEY_ID);
        } else if (properties.isReconcile()) {
            jdbcTemplate.update("""
                    UPDATE model_user_configurations
                    SET provider_id = ?, model_name = ?, base_url = ?, key_source_type = 'OFFICIAL_KEY',
                        user_key_id = NULL, official_key_id = ?, status = 'active',
                        updated_at = CURRENT_TIMESTAMP(3)
                    WHERE model_config_id = ? AND user_id = ? AND deleted_at IS NULL
                    """, providerId, properties.getModel().getModelName().trim(),
                    properties.getModel().getBaseUrl().trim(), OFFICIAL_KEY_ID, MODEL_CONFIG_ID, ADMIN_USER_ID);
        }
    }

    private long requireProviderId(String providerCode) {
        Long providerId = jdbcTemplate.query(
                "SELECT provider_id FROM model_providers WHERE lower(code) = lower(?) AND status = 'active'",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                providerCode.trim()
        );
        if (providerId == null) {
            throw new IllegalStateException("Unsupported bootstrap model provider: " + providerCode);
        }
        return providerId;
    }

    private void validate() {
        requireText(properties.getAdmin().getEmail(), "BOOTSTRAP_ADMIN_EMAIL");
        requireText(properties.getAdmin().getPassword(), "BOOTSTRAP_ADMIN_PASSWORD");
        requireText(properties.getModel().getProvider(), "BOOTSTRAP_MODEL_PROVIDER");
        requireText(properties.getModel().getBaseUrl(), "BOOTSTRAP_MODEL_BASE_URL");
        requireText(properties.getModel().getApiKey(), "BOOTSTRAP_MODEL_API_KEY");
        requireText(properties.getModel().getModelName(), "BOOTSTRAP_MODEL_NAME");
    }

    private void requireText(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required");
        }
    }

    private String mask(String apiKey) {
        String normalized = apiKey.trim();
        int visible = Math.min(4, normalized.length());
        return "****" + normalized.substring(normalized.length() - visible);
    }
}
