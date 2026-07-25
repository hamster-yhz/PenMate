package com.penmate.backend.infrastructure.persistence.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;
import com.penmate.backend.domain.novel.importing.NovelImportSession;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NovelImportSessionPostgreSqlTest {
    private static SqlSessionFactory sqlSessions;
    private static NovelImportSessionRepositoryImpl repository;

    @BeforeAll
    static void setup() {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("novel_import_session");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NovelImportSessionMapper.class);
        configuration.addMapper(NovelProjectMapper.class);
        sqlSessions = new SqlSessionFactoryBuilder().build(configuration);
        SqlSession session = sqlSessions.openSession(true);
        repository = new NovelImportSessionRepositoryImpl(session.getMapper(NovelImportSessionMapper.class),
                new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules()));
    }

    @BeforeEach
    void clear() {
        try (SqlSession session = sqlSessions.openSession(true)) {
            session.getConnection().createStatement().execute("DELETE FROM novel_import_volume_map");
            session.getConnection().createStatement().execute("DELETE FROM novel_import_sessions");
            session.getConnection().createStatement().execute("DELETE FROM novel_projects");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void persists_and_restores_the_format_neutral_draft() {
        NovelImportDraft draft = draft("原始标题");
        NovelImportSession session = new NovelImportSession();
        session.setSessionId(7001L);
        session.setOwnerUserId(1001L);
        session.setOriginalFilename("novel.md");
        session.setDraft(draft);
        session.setStatus("DRAFT");

        assertThat(repository.insert(session)).isEqualTo(1);
        assertThat(repository.findByIdAndOwner(7001L, 1001L).getDraft()).isEqualTo(draft);

        NovelImportDraft adjusted = draft("调整标题");
        assertThat(repository.confirm(7001L, 1001L, adjusted)).isEqualTo(1);
        assertThat(repository.findById(7001L).getDraft().projectTitle()).isEqualTo("调整标题");
        assertThat(repository.findById(7001L).getStatus()).isEqualTo("READY");
    }

    @Test
    void hides_importing_projects_from_normal_project_queries() throws Exception {
        try (SqlSession session = sqlSessions.openSession(true)) {
            session.getConnection().createStatement().execute("""
                    INSERT INTO novel_projects(project_id, owner_user_id, title, genre, status)
                    VALUES (9001, 1001, '导入中', '其他', 0), (9002, 1001, '已发布', '其他', 1)
                    """);
            NovelProjectMapper projects = session.getMapper(NovelProjectMapper.class);
            assertThat(projects.findAll()).extracting("projectId").containsExactly(9002L);
            assertThat(projects.findByProjectId(9001L)).isNull();
            assertThat(projects.findByProjectId(9002L)).isNotNull();
        }
    }

    private NovelImportDraft draft(String title) {
        return new NovelImportDraft(title, NovelImportFormat.MARKDOWN, List.of(
                new NovelImportDraft.Volume("第一卷", List.of(
                        new NovelImportDraft.Chapter("第一章", "正文。")))), List.of()).withDiagnostics();
    }
}
