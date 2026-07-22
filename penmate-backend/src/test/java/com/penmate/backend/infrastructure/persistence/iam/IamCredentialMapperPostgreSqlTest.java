package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamUser;
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

import static org.assertj.core.api.Assertions.assertThat;

class IamCredentialMapperPostgreSqlTest {
    private static final long USER_ID = 935001L;
    private static DataSource dataSource;
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("iam_credential_mapper");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IamUserMapper.class);
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void seedUser() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM iam_users WHERE user_id = " + USER_ID);
            statement.execute("""
                    INSERT INTO iam_users(user_id, email, password_hash, display_name, bio, status)
                    VALUES (935001, 'old@penmate.local', 'old-hash', 'Old Name', 'Old Bio', 1)
                    """);
        }
    }

    @Test
    void profile_updates_cannot_change_email_but_the_credential_operation_can() throws Exception {
        try (SqlSession session = sessions.openSession(true)) {
            IamUserMapper mapper = session.getMapper(IamUserMapper.class);
            IamUser profile = new IamUser();
            profile.setUserId(USER_ID);
            profile.setDisplayName("New Name");
            profile.setBio("New Bio");
            profile.setEmail("ignored@penmate.local");

            assertThat(mapper.updateOwnProfile(profile)).isEqualTo(1);
        }

        assertThat(emailOfUser()).isEqualTo("old@penmate.local");

        try (SqlSession session = sessions.openSession(true)) {
            IamUserMapper mapper = session.getMapper(IamUserMapper.class);
            assertThat(mapper.updateEmail(USER_ID, "new@penmate.local")).isEqualTo(1);
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT email, display_name, bio FROM iam_users WHERE user_id = " + USER_ID)) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("email")).isEqualTo("new@penmate.local");
            assertThat(row.getString("display_name")).isEqualTo("New Name");
            assertThat(row.getString("bio")).isEqualTo("New Bio");
        }
    }

    private String emailOfUser() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT email FROM iam_users WHERE user_id = " + USER_ID)) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }
}
