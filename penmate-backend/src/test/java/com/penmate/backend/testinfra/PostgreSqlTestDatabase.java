package com.penmate.backend.testinfra;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class PostgreSqlTestDatabase {

    private static final String DRIVER = "org.postgresql.Driver";
    private static final String MODE = System.getProperty("penmate.test.database.mode", "local");
    private static final PostgreSQLContainer<?> CONTAINER = createContainer();

    private PostgreSqlTestDatabase() {
    }

    public static DataSource migratedDataSource(String testName) {
        DataSource dataSource = isolatedDataSource(testName);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
        return dataSource;
    }

    public static DataSource isolatedDataSource(String testName) {
        String schema = schemaName(testName);
        DatabaseConfig config = databaseConfig();
        DataSource adminDataSource = new UnpooledDataSource(
                DRIVER, config.url(), config.username(), config.password());
        recreateSchema(adminDataSource, schema);
        return new UnpooledDataSource(
                DRIVER, appendCurrentSchema(config.url(), schema), config.username(), config.password());
    }

    public static String schemaName(String testName) {
        String normalized = testName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (normalized.isBlank() || normalized.length() > 48) {
            throw new IllegalArgumentException("Invalid PostgreSQL test schema name: " + testName);
        }
        return "test_" + normalized;
    }

    public static Set<String> columnsOf(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(
                     null, schemaNameFromConnection(connection), tableName, null)) {
            Set<String> names = new LinkedHashSet<>();
            while (columns.next()) names.add(columns.getString("COLUMN_NAME"));
            return names;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot inspect PostgreSQL table " + tableName, exception);
        }
    }

    public static Set<String> indexesOf(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
             ResultSet indexes = connection.getMetaData().getIndexInfo(
                     null, schemaNameFromConnection(connection), tableName, false, false)) {
            Set<String> names = new LinkedHashSet<>();
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null) names.add(name.toLowerCase(Locale.ROOT));
            }
            return names;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot inspect PostgreSQL indexes for " + tableName, exception);
        }
    }

    private static PostgreSQLContainer<?> createContainer() {
        if (!"container".equalsIgnoreCase(MODE)) return null;
        DockerImageName image = DockerImageName.parse("pgvector/pgvector:0.8.5-pg18")
                .asCompatibleSubstituteFor("postgres");
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(image)
                .withDatabaseName("penmate_test")
                .withUsername("penmate")
                .withPassword("penmate");
        container.start();
        return container;
    }

    private static DatabaseConfig databaseConfig() {
        if (CONTAINER != null) {
            return new DatabaseConfig(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        }
        return new DatabaseConfig(
                System.getProperty("penmate.test.database.url", "jdbc:postgresql://localhost:5432/penmate_test"),
                System.getProperty("penmate.test.database.username", "postgres"),
                System.getProperty("penmate.test.database.password", "postgres"));
    }

    private static void recreateSchema(DataSource dataSource, String schema) {
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public");
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            statement.execute("CREATE SCHEMA " + schema);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot prepare PostgreSQL test schema '" + schema
                            + "'. Create the penmate_test database and configure the local-postgresql-tests profile.",
                    exception);
        }
    }

    private static String appendCurrentSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
    }

    private static String schemaNameFromConnection(Connection connection) throws SQLException {
        return connection.getSchema();
    }

    private record DatabaseConfig(String url, String username, String password) {
    }
}
