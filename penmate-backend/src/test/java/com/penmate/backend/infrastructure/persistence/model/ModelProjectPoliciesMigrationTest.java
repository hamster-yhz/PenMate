package com.penmate.backend.infrastructure.persistence.model;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
 import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProjectPoliciesMigrationTest {

    @Test
    void flywayMigrationsShouldCreateModelProjectPoliciesTable() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.39")
                .withDatabaseName("penmate")
                .withUsername("test")
                .withPassword("test")) {
            mysql.start();

            Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "model_project_policies", null)) {
                assertThat(tables.next()).isTrue();
            }
        }
    }
}
