package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NovelChapterMapperDbCaseTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("novel_chapter_order");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NovelChapterMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM novel_chapters WHERE project_id = 77");
            statement.execute("DELETE FROM novel_volumes WHERE project_id = 77");
            statement.execute("""
                    INSERT INTO novel_volumes(id, volume_id, project_id, title, sort_order)
                    VALUES (1, 101, 77, 'Volume 2', 20), (2, 102, 77, 'Volume 1', 10)
                    """);
            insertChapter(statement, 5, 5005, 101L, 1);
            insertChapter(statement, 4, 5004, 102L, 100);
            insertChapter(statement, 3, 5003, 102L, 5);
            insertChapter(statement, 2, 5002, 102L, 5);
        }
    }

    @Test
    void should_order_by_volume_then_chapter_then_row_id() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<NovelChapter> chapters = session.getMapper(NovelChapterMapper.class).findByProjectId(77L);

            assertThat(chapters)
                    .extracting(NovelChapter::getChapterId)
                    .containsExactly(5002L, 5003L, 5004L, 5005L);
        }
    }

    @Test
    void user_revisions_compete_optimistically_and_ai_lease_blocks_every_user_save() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            NovelChapterMapper mapper = session.getMapper(NovelChapterMapper.class);

            assertThat(mapper.updateUserContent(77L, 5002L, 1L, "用户页面 A", 5)).isEqualTo(1);
            assertThat(mapper.updateUserContent(77L, 5002L, 1L, "用户页面 B", 5)).isZero();

            assertThat(mapper.acquireAiLease(77L, 5002L, 9001L, "ai-token",
                    Instant.now().plusSeconds(60))).isEqualTo(1);
            assertThat(mapper.updateUserContent(77L, 5002L, 2L, "用户不能覆盖", 6)).isZero();
            assertThat(mapper.updateAiContent(77L, 5002L, "wrong-token", 2L, "错误 AI", 4)).isZero();
            assertThat(mapper.updateAiContent(77L, 5002L, "ai-token", 2L, "AI 正文", 4)).isEqualTo(1);

            NovelChapter saved = mapper.findByIdAndProjectId(77L, 5002L);
            assertThat(saved.getContent()).isEqualTo("AI 正文");
            assertThat(saved.getContentRevision()).isEqualTo(3L);
        }
    }

    private void insertChapter(Statement statement, long id, long chapterId, Long volumeId, int sortOrder) throws Exception {
        statement.execute("""
                INSERT INTO novel_chapters(
                    id, chapter_id, project_id, volume_id, title, sort_order, word_count, content,
                    content_revision, created_at, updated_at, deleted_at
                ) VALUES (%d, %d, 77, %d, 'chapter', %d, 0, '', 1,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """.formatted(id, chapterId, volumeId, sortOrder));
    }
}
