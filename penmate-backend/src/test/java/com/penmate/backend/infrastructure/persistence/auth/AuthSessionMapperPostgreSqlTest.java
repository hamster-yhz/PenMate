package com.penmate.backend.infrastructure.persistence.auth;

import com.penmate.backend.domain.auth.model.AuthSession;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionMapperPostgreSqlTest {
    private static final long USER_ID = 936001L;
    private static final long OTHER_USER_ID = 936002L;
    private static DataSource dataSource;
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("auth_session_mapper");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AuthSessionMapper.class);
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void seedSessions() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM auth_sessions WHERE user_id IN (936001, 936002)");
            statement.execute("""
                    INSERT INTO auth_sessions(
                        session_id, user_id, current_access_jti, current_refresh_jti_hash,
                        device_name, browser_name, operating_system, user_agent, ip_address, refresh_expires_at
                    ) VALUES
                        ('current-session', 936001, 'current-access', 'current-refresh',
                         'Desktop', 'Chrome', 'Windows', 'Chrome', '127.0.0.1', CURRENT_TIMESTAMP + INTERVAL '7 days'),
                        ('other-session-1', 936001, 'other-access-1', 'other-refresh-1',
                         'Mobile', 'Safari', 'iOS', 'Safari', '10.0.0.1', CURRENT_TIMESTAMP + INTERVAL '7 days'),
                        ('other-session-2', 936001, 'other-access-2', 'other-refresh-2',
                         'Desktop', 'Firefox', 'Linux', 'Firefox', '10.0.0.2', CURRENT_TIMESTAMP + INTERVAL '7 days'),
                        ('unrelated-session', 936002, 'unrelated-access', 'unrelated-refresh',
                         'Desktop', 'Edge', 'Windows', 'Edge', '10.0.0.3', CURRENT_TIMESTAMP + INTERVAL '7 days')
                    """);
        }
    }

    @Test
    void revoke_all_except_atomically_returns_only_the_sessions_it_revoked() throws Exception {
        Instant revokedAt = Instant.parse("2026-07-22T08:00:00Z");
        List<AuthSession> revoked;
        try (SqlSession session = sessions.openSession(true)) {
            revoked = session.getMapper(AuthSessionMapper.class)
                    .revokeAllExcept(USER_ID, "current-session", revokedAt);
        }

        assertThat(revoked).extracting(AuthSession::getSessionId)
                .containsExactlyInAnyOrder("other-session-1", "other-session-2");
        assertThat(revoked).allSatisfy(item -> assertThat(item.getRevokedAt()).isEqualTo(revokedAt));
        assertThat(revokedAt("current-session")).isNull();
        assertThat(revokedAt("unrelated-session")).isNull();
    }

    private Instant revokedAt(String sessionId) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT revoked_at FROM auth_sessions WHERE session_id = '" + sessionId + "'")) {
            assertThat(row.next()).isTrue();
            return row.getTimestamp(1) == null ? null : row.getTimestamp(1).toInstant();
        }
    }
}
