package com.penmate.backend.infrastructure.persistence.storybible;

import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.flywaydb.core.Flyway;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleRepositoryImplTest {

    private static final String JDBC_URL = "jdbc:h2:mem:story_bible_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/story-bible-repository";
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUp() throws Exception {
        sqlSessionFactory = buildSqlSessionFactory();
        recreateSchema();
        seedRows();
    }

    @Test
    void should_find_active_entries_by_project_and_chapter_boundary() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            StoryBibleMapper mapper = session.getMapper(StoryBibleMapper.class);
            StoryBibleRepositoryImpl repository = new StoryBibleRepositoryImpl(mapper);

            List<StoryBibleEntry> entries = repository.findActiveEntries(920001L, 920002L);

            assertThat(entries)
                    .extracting(StoryBibleEntry::getEntryKey)
                    .containsExactly("hero.identity", "city.rule");
            assertThat(entries)
                    .extracting(StoryBibleEntry::getCanonicalStatus)
                    .containsExactly("CANON", "PROPOSED");
            assertThat(entries)
                    .allSatisfy(entry -> {
                        assertThat(entry.getProjectId()).isEqualTo(920001L);
                        assertThat(entry.getSourceRefs()).isNotEmpty();
                    });
            assertThat(entries)
                    .filteredOn(entry -> "hero.identity".equals(entry.getEntryKey()))
                    .singleElement()
                    .satisfies(entry -> {
                        assertThat(entry.getValidFromChapterId()).isEqualTo(920001L);
                        assertThat(entry.getValidToChapterId()).isEqualTo(920002L);
                    });
            assertThat(entries)
                    .filteredOn(entry -> "city.rule".equals(entry.getEntryKey()))
                    .singleElement()
                    .satisfies(entry -> {
                        assertThat(entry.getValidFromChapterId()).isEqualTo(920002L);
                        assertThat(entry.getValidToChapterId()).isNull();
                    });
        }
    }

    @Test
    void should_exclude_entries_beyond_active_story_bible_version() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            StoryBibleMapper mapper = session.getMapper(StoryBibleMapper.class);
            StoryBibleRepositoryImpl repository = new StoryBibleRepositoryImpl(mapper);

            List<StoryBibleEntry> entries = repository.findActiveEntries(920001L, 920002L);

            assertThat(entries)
                    .extracting(StoryBibleEntry::getEntryKey)
                    .doesNotContain("future.version.only");
        }
    }

    private static SqlSessionFactory buildSqlSessionFactory() {
        DataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                JDBC_URL,
                "sa",
                ""
        );
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(StoryBibleMapper.class);
        configuration.getTypeAliasRegistry().registerAlias("StoryBibleEntry", StoryBibleEntry.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void recreateSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS story_bible_entries");
            statement.execute("DROP TABLE IF EXISTS story_bible_versions");
            statement.execute("DROP TABLE IF EXISTS story_bibles");
            statement.execute("DROP TABLE IF EXISTS novel_projects");
        }
        prepareStoryBibleMigrationsOnly();
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    private static void seedRows() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO novel_projects (id, project_id, owner_user_id, title, summary, status, created_at, updated_at, deleted_at)
                    VALUES (920001, 920001, 920002, 'DBCASE Story Bible Project', 'story bible repo test', 1, NOW(3), NOW(3), NULL)
                    """);
            statement.execute("""
                    INSERT INTO story_bibles (id, story_bible_id, project_id, title, description, active_version_no, created_at, updated_at, deleted_at)
                    VALUES (930001, 930001, 920001, '长夜行 Story Bible', 'project knowledge base', 2, NOW(3), NOW(3), NULL)
                    """);
            statement.execute("""
                    INSERT INTO story_bible_versions (id, version_id, story_bible_id, project_id, version_no, change_summary, created_by, created_at)
                    VALUES
                    (930011, 930011, 930001, 920001, 1, 'init canon baseline', 920002, NOW(3)),
                    (930012, 930012, 930001, 920001, 2, 'add chapter scoped proposal', 920002, NOW(3))
                    """);
            statement.execute("""
                    INSERT INTO story_bible_entries
                    (id, entry_id, story_bible_id, project_id, entry_type, entry_key, title, content, canonical_status, risk_level, source_refs_json, valid_from_chapter_id, valid_to_chapter_id, version_no, created_at, updated_at, deleted_at)
                    VALUES
                    (930101, 930101, 930001, 920001, 'character', 'hero.identity', '主角身份', '林烬是守夜人见习生', 'CANON', 1, '[{"refType":"chapter","refId":920001,"note":"chapter-1"}]', 920001, 920002, 1, NOW(3), NOW(3), NULL),
                    (930102, 930102, 930001, 920001, 'world', 'city.rule', '城规提案', '灰烬城夜禁延长一小时', 'PROPOSED', 2, '[{"refType":"chapter","refId":920002,"note":"chapter-2"}]', 920002, NULL, 2, NOW(3), NOW(3), NULL),
                    (930103, 930103, 930001, 920001, 'plot', 'future.twist', '未来反转', '第三章后才成立', 'CANON', 2, '[{"refType":"chapter","refId":920003,"note":"chapter-3"}]', 920003, NULL, 2, NOW(3), NOW(3), NULL),
                    (930104, 930104, 930001, 920001, 'world', 'old.rule', '过期规则', '第一章后废弃', 'CANON', 1, '[{"refType":"chapter","refId":920001,"note":"expired"}]', NULL, 920001, 1, NOW(3), NOW(3), NULL),
                    (930105, 930105, 930001, 920001, 'plot', 'unsafe.assumption', '待核实假设', '不能进入当前上下文', 'ASSUMPTION', 3, '[{"refType":"chapter","refId":920002,"note":"assumption"}]', 920001, NULL, 2, NOW(3), NOW(3), NULL),
                    (930106, 930106, 930001, 920002, 'character', 'other.project', '其他项目条目', '必须隔离', 'CANON', 1, '[{"refType":"chapter","refId":920002,"note":"other-project"}]', 920001, NULL, 2, NOW(3), NOW(3), NULL),
                    (930107, 930107, 930001, 920001, 'plot', 'future.version.only', '未来版本条目', '版本 3 才能生效', 'CANON', 2, '[{"refType":"chapter","refId":920002,"note":"future-version"}]', 920001, NULL, 3, NOW(3), NOW(3), NULL)
                    """);
        }
    }

    private static void prepareStoryBibleMigrationsOnly() throws IOException {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql"),
                migrationDir.resolve("V2__init_novel_and_approval_minimal.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
                Path.of("src/main/resources/db/migration/V12__init_story_bible_domain.sql"),
                migrationDir.resolve("V12__init_story_bible_domain.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}
