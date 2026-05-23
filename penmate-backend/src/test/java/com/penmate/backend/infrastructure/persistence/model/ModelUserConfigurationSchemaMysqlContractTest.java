package com.penmate.backend.infrastructure.persistence.model;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModelUserConfigurationSchemaMysqlContractTest {

    private static final String JDBC_URL = "jdbc:h2:mem:model_user_configuration_schema_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/model-user-configuration-schema";

    @BeforeAll
    static void migrateSchema() throws IOException {
        prepareModelMigrationsOnly();
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    @Test
    void should_define_model_user_configuration_max_context_tokens_column() throws Exception {
        assertThat(columnsOf("model_user_configurations"))
                .contains("model_config_id", "context_window_turns", "max_context_tokens");

        String v10Sql = Files.readString(Path.of("src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql"));
        assertThat(v10Sql)
                .contains("max_context_tokens INT UNSIGNED NOT NULL DEFAULT 128000")
                .contains("模型最大上下文窗口 token 数");
    }

    private Set<String> columnsOf(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {
            Set<String> names = new LinkedHashSet<>();
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME"));
            }
            return names;
        }
    }

    private static void prepareModelMigrationsOnly() throws IOException {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql"),
                migrationDir.resolve("V10__init_plugin_and_model_domains.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}
