package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModelUserConfigurationSchemaPostgreSqlContractTest {

    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("model_user_configuration_schema_contract");
    }

    @Test
    void should_define_unified_model_configuration_context_columns() throws Exception {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "model_configurations"))
                .contains("model_config_id", "scope_type", "owner_user_id", "model_type",
                        "context_window_turns", "max_context_tokens", "reasoning_effort",
                        "reasoning_mode", "reasoning_summary");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "model_user_configurations")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "model_user_api_keys"))
                .contains("model_config_id", "user_id", "encrypted_api_key", "masked_api_key");

        String sql = Files.readString(Path.of("src/main/resources/db/migration/V4__create_plugin_and_model.sql"));
        assertThat(sql).contains("max_context_tokens INTEGER NOT NULL DEFAULT 128000");
    }
}
