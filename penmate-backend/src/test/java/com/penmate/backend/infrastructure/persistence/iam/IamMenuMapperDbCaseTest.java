package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamMenu;
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

class IamMenuMapperDbCaseTest {

    private static final Long DBCASE_ADMIN_USER_ID = 920001L;
    private static final Long DBCASE_OWNER_USER_ID = 920002L;
    private static final Long DBCASE_EDITOR_USER_ID = 920003L;
    private static final Long DBCASE_FROZEN_USER_ID = 920004L;

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("iam_menu_dbcase");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IamMenuMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetDbCaseSeed() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration()
                .getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM iam_user_roles WHERE user_id BETWEEN 920001 AND 920004");
            statement.execute("DELETE FROM iam_role_permissions WHERE role_id = 920001");
            statement.execute("DELETE FROM iam_menus WHERE menu_id = 920001");
            statement.execute("DELETE FROM iam_permissions WHERE permission_id = 920001");
            statement.execute("DELETE FROM iam_roles WHERE role_id = 920001");
            statement.execute("DELETE FROM iam_users WHERE user_id BETWEEN 920001 AND 920004");
            statement.execute("""
                    INSERT INTO iam_users(user_id, email, password_hash, display_name, status)
                    VALUES
                      (920001, 'dbcase_admin@penmate.local', 'hash', 'DBCASE Admin', 1),
                      (920002, 'dbcase_owner@penmate.local', 'hash', 'DBCASE Owner', 1),
                      (920003, 'dbcase_editor@penmate.local', 'hash', 'DBCASE Editor', 1),
                      (920004, 'dbcase_frozen@penmate.local', 'hash', 'DBCASE Frozen', 2)
                    """);
            statement.execute("""
                    INSERT INTO iam_roles(role_id, name, code, is_system)
                    VALUES (920001, 'DBCASE Administrator', 'dbcase_admin', TRUE)
                    """);
            statement.execute("""
                    INSERT INTO iam_permissions(permission_id, name, code, module)
                    VALUES (920001, 'Manage RBAC', 'admin:rbac', 'iam')
                    """);
            statement.execute("""
                    INSERT INTO iam_menus(menu_id, title, path, sort_order, permission_code, visible)
                    VALUES (920001, 'RBAC', '/admin/rbac', 920001, 'admin:rbac', TRUE)
                    """);
            statement.execute("INSERT INTO iam_user_roles(user_id, role_id) VALUES (920001, 920001)");
            statement.execute("INSERT INTO iam_role_permissions(role_id, permission_id) VALUES (920001, 920001)");
        }
    }

    @Test
    void should_expose_admin_rbac_menu_for_dbcase_admin_user() {
        List<IamMenu> menus;
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            menus = sqlSession.getMapper(IamMenuMapper.class).findVisibleByUserId(DBCASE_ADMIN_USER_ID);
        }
        assertThat(menus).extracting(IamMenu::getPath).contains("/admin/rbac");
    }

    @Test
    void should_not_expose_admin_rbac_menu_for_non_admin_dbcase_users() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            IamMenuMapper mapper = sqlSession.getMapper(IamMenuMapper.class);
            assertThat(mapper.findVisibleByUserId(DBCASE_OWNER_USER_ID))
                    .extracting(IamMenu::getPath).doesNotContain("/admin/rbac");
            assertThat(mapper.findVisibleByUserId(DBCASE_EDITOR_USER_ID))
                    .extracting(IamMenu::getPath).doesNotContain("/admin/rbac");
            assertThat(mapper.findVisibleByUserId(DBCASE_FROZEN_USER_ID))
                    .extracting(IamMenu::getPath).doesNotContain("/admin/rbac");
        }
    }
}
