package com.penmate.backend.infrastructure.persistence.model;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelMapperDbCaseTest {

    private static final String H2_URL = "jdbc:h2:mem:model_mapper_dbcase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory();
    }

    @BeforeEach
    void resetSchema() throws Exception {
        recreateSchema();
        seedRows();
    }

    @Test
    void should_read_max_context_tokens_from_model_user_configuration_queries() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            ModelMapper mapper = sqlSession.getMapper(ModelMapper.class);

            List<Map<String, Object>> listed = mapper.listUserModelConfigs(920002L);
            Map<String, Object> found = mapper.findUserModelConfig(920002L, 920021L);

            assertThat(listed)
                    .singleElement()
                    .satisfies(item -> assertThat(normalizeKeys(item))
                            .containsEntry("modelconfigid", 920021L)
                            .containsEntry("maxcontexttokens", 64000));
            assertThat(normalizeKeys(found))
                    .containsEntry("modelconfigid", 920021L)
                    .containsEntry("maxcontexttokens", 64000)
                    .containsEntry("keyname", "DBCASE Owner OpenAI 主 Key")
                    .containsEntry("maskedapikey", "****92011");
        }
    }

    @Test
    void should_persist_max_context_tokens_when_inserting_user_model_config() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            ModelMapper mapper = sqlSession.getMapper(ModelMapper.class);

            assertThat(mapper.insertUserModelConfig(920031L, 920002L, 1L, "gpt-4.1", null, "USER_KEY", 920011L, null, 8, 200000, "active"))
                    .isEqualTo(1);
            assertThat(singleLong("SELECT max_context_tokens FROM model_user_configurations WHERE model_config_id = 920031"))
                    .isEqualTo(200000L);
        }
    }

    @Test
    void should_persist_max_context_tokens_when_updating_user_model_config() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            ModelMapper mapper = sqlSession.getMapper(ModelMapper.class);

            assertThat(mapper.updateUserModelConfig(920002L, 920021L, 1L, "gpt-4o-mini", null, "USER_KEY", 920011L, null, 6, 32000, "active"))
                    .isEqualTo(1);
            assertThat(singleLong("SELECT max_context_tokens FROM model_user_configurations WHERE model_config_id = 920021"))
                    .isEqualTo(32000L);
        }
    }

    private static SqlSessionFactory buildSqlSessionFactory() {
        DataSource dataSource = new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
                "org.h2.Driver",
                H2_URL,
                "sa",
                "");

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ModelMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void recreateSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS model_user_configurations");
            statement.execute("DROP TABLE IF EXISTS model_official_api_keys");
            statement.execute("DROP TABLE IF EXISTS model_user_api_keys");
            statement.execute("""
                    CREATE TABLE model_user_api_keys (
                        id BIGINT PRIMARY KEY,
                        user_api_key_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        provider_id BIGINT NOT NULL,
                        key_name VARCHAR(120) NOT NULL,
                        encrypted_api_key VARCHAR(255) NOT NULL,
                        masked_api_key VARCHAR(40) NULL,
                        is_default TINYINT NOT NULL,
                        last_used_at TIMESTAMP NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE model_official_api_keys (
                        id BIGINT PRIMARY KEY,
                        official_api_key_id BIGINT NOT NULL,
                        provider_id BIGINT NOT NULL,
                        key_name VARCHAR(120) NOT NULL,
                        encrypted_api_key VARCHAR(255) NOT NULL,
                        masked_api_key VARCHAR(40) NULL,
                        is_default TINYINT NOT NULL,
                        last_used_at TIMESTAMP NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE model_user_configurations (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        model_config_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        provider_id BIGINT NOT NULL,
                        model_name VARCHAR(120) NOT NULL,
                        base_url VARCHAR(255) NULL,
                        key_source_type VARCHAR(20) NOT NULL,
                        user_key_id BIGINT NULL,
                        official_key_id BIGINT NULL,
                        context_window_turns INT NOT NULL,
                        max_context_tokens INT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
        }
    }

    private static void seedRows() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO model_user_api_keys(
                        id, user_api_key_id, user_id, provider_id, key_name, encrypted_api_key,
                        masked_api_key, is_default, last_used_at, status, created_at, updated_at, deleted_at
                    ) VALUES (
                        920001, 920011, 920002, 1, 'DBCASE Owner OpenAI 主 Key', 'cipher-user-openai-920011',
                        '****92011', 1, CURRENT_TIMESTAMP, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO model_user_configurations(
                        id, model_config_id, user_id, provider_id, model_name, base_url,
                        key_source_type, user_key_id, official_key_id, context_window_turns,
                        max_context_tokens, status, created_at, updated_at, deleted_at
                    ) VALUES (
                        920001, 920021, 920002, 1, 'gpt-4o-mini', NULL,
                        'USER_KEY', 920011, NULL, 6,
                        64000, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
                    )
                    """);
        }
    }

    private static long singleLong(String sql) throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static Map<String, Object> normalizeKeys(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }
}
