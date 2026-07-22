package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportVolumeCommand;
import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NovelTxtImportPostgreSqlTest {

    private static final long OWNER_ID = 940099L;
    private static DataSource dataSource;
    private static TransactionTemplate transactions;
    private static NovelGatewayImpl gateway;
    private AtomicLong ids;
    private StoryBibleApplicationService storyBible;
    private ProjectAiConfigurationService projectAi;
    private NovelApplicationService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("novel_txt_import");
        Configuration configuration = new Configuration(new Environment(
                "test", new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NovelProjectMapper.class);
        configuration.addMapper(NovelVolumeMapper.class);
        configuration.addMapper(NovelChapterMapper.class);
        configuration.addMapper(ChapterAiUndoMapper.class);
        SqlSessionFactory sessions = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(sessions);
        gateway = new NovelGatewayImpl(
                template.getMapper(NovelProjectMapper.class),
                template.getMapper(NovelVolumeMapper.class),
                template.getMapper(NovelChapterMapper.class),
                template.getMapper(ChapterAiUndoMapper.class));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM novel_chapter_ai_undo_operations");
            statement.execute("DELETE FROM novel_chapters");
            statement.execute("DELETE FROM novel_volumes");
            statement.execute("DELETE FROM novel_projects");
        }
        ids = new AtomicLong(940000L);
        storyBible = mock(StoryBibleApplicationService.class);
        projectAi = mock(ProjectAiConfigurationService.class);
        service = new NovelApplicationService(gateway, ids::incrementAndGet, storyBible, projectAi);
    }

    @Test
    void commits_project_directory_and_chapter_content_together() throws Exception {
        transactions.executeWithoutResult(status -> service.createImportedProject(command(), "trace-import"));

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(count(statement, "novel_projects")).isOne();
            assertThat(count(statement, "novel_volumes")).isOne();
            assertThat(count(statement, "novel_chapters")).isEqualTo(2);
            try (ResultSet rows = statement.executeQuery("""
                    SELECT title, content, word_count, sort_order
                    FROM novel_chapters ORDER BY sort_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("title")).isEqualTo("第一章 来客");
                assertThat(rows.getString("content")).isEqualTo("雨水落下。");
                assertThat(rows.getInt("word_count")).isEqualTo(5);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("title")).isEqualTo("第二章 回声");
                assertThat(rows.getInt("sort_order")).isEqualTo(2);
            }
        }
        verify(storyBible).bootstrap(940001L, "导入作品", OWNER_ID);
        verify(projectAi).initializeProject(940001L, OWNER_ID);
    }

    @Test
    void rolls_back_every_imported_row_when_project_initialization_fails() throws Exception {
        doThrow(new IllegalStateException("bootstrap failed"))
                .when(storyBible).bootstrap(940001L, "导入作品", OWNER_ID);

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> service.createImportedProject(command(), "trace-import")))
                .hasMessage("bootstrap failed");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(count(statement, "novel_projects")).isZero();
            assertThat(count(statement, "novel_volumes")).isZero();
            assertThat(count(statement, "novel_chapters")).isZero();
        }
    }

    private ImportProjectCommand command() {
        return new ImportProjectCommand(
                new CreateProjectCommand(OWNER_ID, "导入作品", null, "其他", null, List.of(), 1),
                List.of(new ImportVolumeCommand("第一卷 雨夜", List.of(
                        new ImportChapterCommand("第一章 来客", "雨水落下。"),
                        new ImportChapterCommand("第二章 回声", "城门合拢。")
                ))));
    }

    private long count(Statement statement, String table) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
