package com.penmate.backend.infrastructure.bootstrap;

import com.penmate.backend.application.model.ModelCapabilityCatalogService;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemDataBootstrapPostgreSqlTest {

    @Test
    void creates_once_and_reconciles_only_when_enabled() throws Exception {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("system_data_bootstrap");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        SecretCryptoService crypto = mock(SecretCryptoService.class);
        when(crypto.encrypt("first-key")).thenReturn("encrypted-first-key");
        when(crypto.encrypt("second-key")).thenReturn("encrypted-second-key");

        SystemBootstrapProperties properties = properties(
                "Admin@PenMate.Local", "first-password", "first-key", "first-model");
        SystemDataBootstrap bootstrap = new SystemDataBootstrap(
                jdbc, passwordEncoder, crypto, properties, new ModelCapabilityCatalogService());

        bootstrap.run(mock(ApplicationArguments.class));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM iam_users WHERE user_id = 1", String.class))
                .isEqualTo("admin@penmate.local");
        assertThat(passwordEncoder.matches("first-password", jdbc.queryForObject(
                "SELECT password_hash FROM iam_users WHERE user_id = 1", String.class))).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT model_name FROM model_configurations WHERE model_config_id = 1", String.class))
                .isEqualTo("first-model");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_user_roles WHERE user_id = 1 AND role_id = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_roles WHERE is_system", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_permissions", Integer.class))
                .isEqualTo(33);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_role_permissions WHERE role_id = 2", Integer.class))
                .isEqualTo(17);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_role_permissions WHERE role_id = 3", Integer.class))
                .isEqualTo(1);

        properties.getAdmin().setEmail("second@penmate.local");
        properties.getAdmin().setPassword("second-password");
        properties.getChat().setApiKey("second-key");
        properties.getChat().setModelName("second-model");
        bootstrap.run(mock(ApplicationArguments.class));

        assertThat(jdbc.queryForObject("SELECT email FROM iam_users WHERE user_id = 1", String.class))
                .isEqualTo("admin@penmate.local");
        assertThat(jdbc.queryForObject(
                "SELECT model_name FROM model_configurations WHERE model_config_id = 1", String.class))
                .isEqualTo("first-model");

        properties.setReconcile(true);
        bootstrap.run(mock(ApplicationArguments.class));

        assertThat(jdbc.queryForObject("SELECT email FROM iam_users WHERE user_id = 1", String.class))
                .isEqualTo("second@penmate.local");
        assertThat(passwordEncoder.matches("second-password", jdbc.queryForObject(
                "SELECT password_hash FROM iam_users WHERE user_id = 1", String.class))).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT model_name FROM model_configurations WHERE model_config_id = 1", String.class))
                .isEqualTo("second-model");
        assertThat(jdbc.queryForObject(
                "SELECT encrypted_api_key FROM model_official_api_keys WHERE official_api_key_id = 1", String.class))
                .isEqualTo("encrypted-second-key");
    }

    private SystemBootstrapProperties properties(
            String email, String password, String apiKey, String modelName) {
        SystemBootstrapProperties properties = new SystemBootstrapProperties();
        properties.getAdmin().setEmail(email);
        properties.getAdmin().setPassword(password);
        properties.getChat().setProvider("openai-compatible");
        properties.getChat().setBaseUrl("http://localhost:11434/v1");
        properties.getChat().setApiKey(apiKey);
        properties.getChat().setModelName(modelName);
        return properties;
    }
}
