package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamMenu;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IamMenuMapperDbCaseTest {

    private static final Long DBCASE_ADMIN_USER_ID = 920001L;
    private static final Long DBCASE_OWNER_USER_ID = 920002L;
    private static final Long DBCASE_EDITOR_USER_ID = 920003L;
    private static final Long DBCASE_FROZEN_USER_ID = 920004L;

    private static final String H2_URL = "jdbc:h2:mem:iam_menu_dbcase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory();
    }

    @BeforeEach
    void resetDbCaseSeed() throws Exception {
        recreateIamSchema();
        executeIamSeedBlock();
    }

    @Test
    void should_expose_admin_rbac_menu_for_dbcase_admin_user() {
        List<IamMenu> menus;
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            IamMenuMapper mapper = sqlSession.getMapper(IamMenuMapper.class);
            menus = mapper.findVisibleByUserId(DBCASE_ADMIN_USER_ID);
        }

        assertThat(menus)
                .extracting(IamMenu::getPath)
                .contains("/admin/rbac");
    }

    @Test
    void should_not_expose_admin_rbac_menu_for_non_admin_dbcase_users() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            IamMenuMapper mapper = sqlSession.getMapper(IamMenuMapper.class);

            assertThat(mapper.findVisibleByUserId(DBCASE_OWNER_USER_ID))
                    .extracting(IamMenu::getPath)
                    .doesNotContain("/admin/rbac");
            assertThat(mapper.findVisibleByUserId(DBCASE_EDITOR_USER_ID))
                    .extracting(IamMenu::getPath)
                    .doesNotContain("/admin/rbac");
            assertThat(mapper.findVisibleByUserId(DBCASE_FROZEN_USER_ID))
                    .extracting(IamMenu::getPath)
                    .doesNotContain("/admin/rbac");
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
        configuration.addMapper(IamMenuMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void recreateIamSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration()
                .getEnvironment()
                .getDataSource()
                .getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE iam_users (
                        id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        email VARCHAR(255) NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        display_name VARCHAR(80) NOT NULL,
                        status TINYINT NOT NULL,
                        auth_method VARCHAR(32) NOT NULL,
                        last_login_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE iam_roles (
                        id BIGINT PRIMARY KEY,
                        role_id BIGINT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        code VARCHAR(100) NOT NULL,
                        description VARCHAR(255) NULL,
                        is_system TINYINT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE iam_permissions (
                        id BIGINT PRIMARY KEY,
                        permission_id BIGINT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        code VARCHAR(120) NOT NULL,
                        module VARCHAR(60) NOT NULL,
                        description VARCHAR(255) NULL,
                        created_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE iam_user_roles (
                        user_id BIGINT NOT NULL,
                        role_id BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        PRIMARY KEY (user_id, role_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE iam_role_permissions (
                        role_id BIGINT NOT NULL,
                        permission_id BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        PRIMARY KEY (role_id, permission_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE iam_menus (
                        id BIGINT PRIMARY KEY,
                        menu_id BIGINT NOT NULL,
                        parent_id BIGINT NULL,
                        title VARCHAR(100) NOT NULL,
                        path VARCHAR(255) NOT NULL,
                        sort_order INT NOT NULL,
                        permission_code VARCHAR(120) NULL,
                        visible TINYINT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
        }
    }

    private static void executeIamSeedBlock() throws Exception {
        String seedSql;
        try (var inputStream = new ClassPathResource("db/cases/seed_all_domain_base.sql").getInputStream()) {
            seedSql = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }

        int iamStart = seedSql.indexOf("-- IAM");
        int iamEnd = seedSql.indexOf("-- 小说核心");
        String iamBlock = seedSql.substring(iamStart, iamEnd)
                .replaceAll("(?m)^\\s*--.*$", "")
                .replace("NOW(3)", "CURRENT_TIMESTAMP");

        try (Connection connection = sqlSessionFactory.getConfiguration()
                .getEnvironment()
                .getDataSource()
                .getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : iamBlock.split(";")) {
                String trimmed = sql.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                statement.execute(trimmed);
            }
        }
    }
}
