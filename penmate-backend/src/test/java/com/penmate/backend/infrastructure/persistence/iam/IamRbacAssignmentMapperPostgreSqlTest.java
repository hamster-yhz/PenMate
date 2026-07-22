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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IamRbacAssignmentMapperPostgreSqlTest {
    private static final long USER_ID = 930001L;
    private static final long ACTOR_ID = 930009L;
    private static final long ROLE_ONE = 930101L;
    private static final long ROLE_TWO = 930102L;
    private static final long PERMISSION_ONE = 930201L;
    private static final long PERMISSION_TWO = 930202L;

    private static SqlSessionFactory sessions;
    private static DataSource dataSource;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("iam_rbac_assignment_mapper");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IamUserMapper.class);
        configuration.addMapper(IamRoleMapper.class);
        configuration.addMapper(IamRbacAuditMapper.class);
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void seedAssignments() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM iam_rbac_assignment_audits WHERE target_id IN (930001, 930101)");
            statement.execute("DELETE FROM iam_user_roles WHERE user_id = 930001");
            statement.execute("DELETE FROM iam_role_permissions WHERE role_id IN (930101, 930102)");
            statement.execute("DELETE FROM iam_permissions WHERE permission_id IN (930201, 930202)");
            statement.execute("DELETE FROM iam_roles WHERE role_id IN (930101, 930102)");
            statement.execute("DELETE FROM iam_users WHERE user_id IN (930001, 930009)");
            statement.execute("""
                    INSERT INTO iam_users(user_id, email, password_hash, display_name, status)
                    VALUES
                      (930001, 'rbac_target@penmate.local', 'hash', 'RBAC Target', 1),
                      (930009, 'rbac_actor@penmate.local', 'hash', 'RBAC Actor', 1)
                    """);
            statement.execute("""
                    INSERT INTO iam_roles(role_id, name, code, is_system)
                    VALUES
                      (930101, 'Role One', 'rbac_role_one', FALSE),
                      (930102, 'Role Two', 'rbac_role_two', FALSE)
                    """);
            statement.execute("""
                    INSERT INTO iam_permissions(permission_id, name, code, module)
                    VALUES
                      (930201, 'Permission One', 'rbac.permission.one', 'rbac'),
                      (930202, 'Permission Two', 'rbac.permission.two', 'rbac')
                    """);
            statement.execute("INSERT INTO iam_user_roles(user_id, role_id) VALUES (930001, 930101)");
            statement.execute("INSERT INTO iam_role_permissions(role_id, permission_id) VALUES (930101, 930201)");
        }
    }

    @Test
    void commits_replacement_revision_and_complete_audit_together() throws Exception {
        try (SqlSession session = sessions.openSession(false)) {
            IamUserMapper users = session.getMapper(IamUserMapper.class);
            IamRbacAuditMapper audits = session.getMapper(IamRbacAuditMapper.class);

            assertThat(users.lockRbacRevision(USER_ID)).isZero();
            users.deleteAllRoles(USER_ID);
            users.insertRoles(USER_ID, List.of(ROLE_TWO));
            assertThat(users.incrementRbacRevision(USER_ID, 0L)).isEqualTo(1);
            assertThat(audits.insert(930301L, ACTOR_ID, "USER_ROLES", USER_ID,
                    "[930101]", "[930102]", 0L, 1L, "trace-dbcase")).isEqualTo(1);
            session.commit();
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(singleLong(statement, "SELECT role_id FROM iam_user_roles WHERE user_id = 930001"))
                    .isEqualTo(ROLE_TWO);
            assertThat(singleLong(statement, "SELECT rbac_revision FROM iam_users WHERE user_id = 930001"))
                    .isEqualTo(1L);
            try (ResultSet result = statement.executeQuery("""
                    SELECT before_ids_json::text, after_ids_json::text, previous_revision, new_revision, trace_id
                    FROM iam_rbac_assignment_audits WHERE audit_id = 930301
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("[930101]");
                assertThat(result.getString(2)).isEqualTo("[930102]");
                assertThat(result.getLong(3)).isZero();
                assertThat(result.getLong(4)).isEqualTo(1L);
                assertThat(result.getString(5)).isEqualTo("trace-dbcase");
            }
        }
    }

    @Test
    void rolls_back_relationship_and_revision_when_the_transaction_does_not_commit() throws Exception {
        try (SqlSession session = sessions.openSession(false)) {
            IamRoleMapper roles = session.getMapper(IamRoleMapper.class);
            assertThat(roles.lockRbacRevision(ROLE_ONE)).isZero();
            roles.deleteAllPermissions(ROLE_ONE);
            roles.insertPermissions(ROLE_ONE, List.of(PERMISSION_TWO));
            assertThat(roles.incrementRbacRevision(ROLE_ONE, 0L)).isEqualTo(1);
            session.rollback();
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(singleLong(statement,
                    "SELECT permission_id FROM iam_role_permissions WHERE role_id = 930101"))
                    .isEqualTo(PERMISSION_ONE);
            assertThat(singleLong(statement, "SELECT rbac_revision FROM iam_roles WHERE role_id = 930101"))
                    .isZero();
        }
    }

    private long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
