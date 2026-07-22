package com.penmate.backend.infrastructure.persistence.iam;

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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IamUserPurgePostgreSqlTest {

    private static final long DUE_USER_ID = 932001L;
    private static final long FUTURE_USER_ID = 932002L;
    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("iam_user_purge");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IamUserMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetRows() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM auth_sessions WHERE user_id IN (" + DUE_USER_ID + ", " + FUTURE_USER_ID + ")");
            statement.execute("DELETE FROM user_ui_preferences WHERE user_id IN (" + DUE_USER_ID + ", " + FUTURE_USER_ID + ")");
            statement.execute("DELETE FROM iam_users WHERE user_id IN (" + DUE_USER_ID + ", " + FUTURE_USER_ID + ")");
            insertUser(statement, DUE_USER_ID, "CURRENT_TIMESTAMP - INTERVAL '1 day'");
            insertUser(statement, FUTURE_USER_ID, "CURRENT_TIMESTAMP + INTERVAL '1 day'");
            statement.execute("INSERT INTO user_ui_preferences(user_id) VALUES (" + DUE_USER_ID + ")");
            statement.execute("""
                    INSERT INTO auth_sessions(
                        session_id, user_id, current_access_jti, current_refresh_jti_hash,
                        device_name, browser_name, operating_system, user_agent, ip_address,
                        refresh_expires_at)
                    VALUES ('purge-session', %d, 'access-jti', 'refresh-jti',
                            'desktop', 'browser', 'os', 'agent', '127.0.0.1',
                            CURRENT_TIMESTAMP + INTERVAL '1 day')
                    """.formatted(DUE_USER_ID));
        }
    }

    @Test
    void purges_only_an_account_whose_waiting_period_has_expired() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            IamUserMapper mapper = session.getMapper(IamUserMapper.class);
            assertThat(mapper.findDeletionDueUserIds(Instant.now()))
                    .contains(DUE_USER_ID)
                    .doesNotContain(FUTURE_USER_ID);
            assertThat(mapper.purgePendingDeletion(FUTURE_USER_ID, Instant.now())).isZero();
            assertThat(mapper.purgePendingDeletion(DUE_USER_ID, Instant.now())).isOne();
        }

        assertThat(rowExists("iam_users", DUE_USER_ID)).isFalse();
        assertThat(rowExists("auth_sessions", DUE_USER_ID)).isFalse();
        assertThat(rowExists("user_ui_preferences", DUE_USER_ID)).isFalse();
        assertThat(rowExists("iam_users", FUTURE_USER_ID)).isTrue();
    }

    private void insertUser(Statement statement, long userId, String dueAt) throws Exception {
        statement.execute("""
                INSERT INTO iam_users(
                    user_id, email, password_hash, display_name, status,
                    deletion_requested_at, deletion_due_at)
                VALUES (%d, 'user-%d@example.test', 'hash', 'Writer', 0,
                        CURRENT_TIMESTAMP - INTERVAL '30 days', %s)
                """.formatted(userId, userId, dueAt));
    }

    private boolean rowExists(String table, long userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE user_id = " + userId + ")")) {
            result.next();
            return result.getBoolean(1);
        }
    }
}
