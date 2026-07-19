package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
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
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelMapperDbCaseTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory(
                PostgreSqlTestDatabase.migratedDataSource("model_mapper_dbcase"));
    }

    @BeforeEach
    void resetSchema() throws Exception {
        execute("DELETE FROM model_user_api_keys WHERE model_config_id BETWEEN 920000 AND 920999");
        execute("DELETE FROM model_official_api_keys WHERE model_config_id BETWEEN 920000 AND 920999");
        execute("DELETE FROM model_configurations WHERE model_config_id BETWEEN 920000 AND 920999");
        seedConfiguration();
    }

    @Test
    void readsUnifiedUserConfigurationAndMaskedCredential() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ModelMapper mapper = session.getMapper(ModelMapper.class);

            List<ModelConfiguration> listed = mapper.listAccessibleConfigurations(920002L);
            ModelConfiguration found = mapper.findAccessibleConfiguration(920002L, 920021L);

            assertThat(listed).anySatisfy(item -> assertThat(item.getModelConfigId()).isEqualTo(920021L));
            assertThat(found).satisfies(item -> {
                assertThat(item.getScopeType()).isEqualTo("USER");
                assertThat(item.getOwnerUserId()).isEqualTo(920002L);
                assertThat(item.getMaxContextTokens()).isEqualTo(64000);
                assertThat(item.getMaskedApiKey()).isEqualTo("****2011");
                assertThat(item.getProtocolCode()).isEqualTo("OPENAI_CHAT_COMPLETIONS");
            });
        }
    }

    @Test
    void insertsAndUpdatesUnifiedConfigurationWithCredential() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ModelMapper mapper = session.getMapper(ModelMapper.class);
            ModelConfiguration configuration = configuration(920031L, 200000);
            ModelCredential credential = new ModelCredential();
            credential.setCredentialId(920032L);
            credential.setEncryptedApiKey("cipher-920032");
            credential.setMaskedApiKey("****0032");
            credential.setStatus("ACTIVE");

            assertThat(mapper.insertConfiguration(configuration)).isEqualTo(1);
            assertThat(mapper.insertUserCredential(configuration, credential)).isEqualTo(1);
            configuration.setMaxContextTokens(32000);
            assertThat(mapper.updateConfiguration(configuration)).isEqualTo(1);

            assertThat(singleLong("SELECT max_context_tokens FROM model_configurations WHERE model_config_id = 920031"))
                    .isEqualTo(32000L);
            assertThat(mapper.findUserCredential(920002L, 920031L).getMaskedApiKey()).isEqualTo("****0032");
        }
    }

    private static SqlSessionFactory buildSqlSessionFactory(DataSource dataSource) {
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ModelMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void seedConfiguration() throws Exception {
        execute("""
                INSERT INTO model_configurations(
                    model_config_id, scope_type, owner_user_id, provider_id, display_name,
                    model_type, model_name, base_url, distance_metric, context_window_turns,
                    max_context_tokens, status, created_by, updated_by
                ) VALUES (
                    920021, 'USER', 920002, 1, 'DBCASE Chat',
                    'CHAT', 'gpt-4o-mini', NULL, NULL, 6,
                    64000, 'ACTIVE', 920002, 920002
                )
                """);
        execute("""
                INSERT INTO model_user_api_keys(
                    user_api_key_id, model_config_id, user_id, provider_id,
                    encrypted_api_key, masked_api_key, status
                ) VALUES (920011, 920021, 920002, 1, 'cipher-920011', '****2011', 'ACTIVE')
                """);
    }

    private static ModelConfiguration configuration(Long id, int maxTokens) {
        ModelConfiguration configuration = new ModelConfiguration();
        configuration.setModelConfigId(id);
        configuration.setScopeType("USER");
        configuration.setOwnerUserId(920002L);
        configuration.setProviderId(1L);
        configuration.setDisplayName("Inserted Chat");
        configuration.setModelType("CHAT");
        configuration.setModelName("gpt-4.1");
        configuration.setContextWindowTurns(8);
        configuration.setMaxContextTokens(maxTokens);
        configuration.setStatus("ACTIVE");
        configuration.setCreatedBy(920002L);
        configuration.setUpdatedBy(920002L);
        return configuration;
    }

    private static long singleLong(String sql) throws Exception {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static DataSource dataSource() {
        return sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
    }
}
