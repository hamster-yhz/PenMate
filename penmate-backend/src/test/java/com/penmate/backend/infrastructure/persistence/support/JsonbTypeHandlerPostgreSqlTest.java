package com.penmate.backend.infrastructure.persistence.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class JsonbTypeHandlerPostgreSqlTest {

    @Test
    void writes_and_reads_jsonb_without_text_casts() throws Exception {
        DataSource dataSource = PostgreSqlTestDatabase.isolatedDataSource("jsonb_type_handler");
        JsonbTypeHandler handler = new JsonbTypeHandler();
        ObjectMapper objectMapper = new ObjectMapper();
        String payload = "{\"enabled\":true,\"items\":[1,2],\"nested\":{\"name\":\"demo\"}}";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE jsonb_round_trip(id BIGINT PRIMARY KEY, payload JSONB NOT NULL)");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO jsonb_round_trip(id, payload) VALUES (1, ?)") ) {
            handler.setParameter(statement, 1, payload, JdbcType.OTHER);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT payload FROM jsonb_round_trip WHERE id = 1");
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            JsonNode actual = objectMapper.readTree(handler.getResult(resultSet, "payload"));
            assertThat(actual).isEqualTo(objectMapper.readTree(payload));
        }
    }
}
