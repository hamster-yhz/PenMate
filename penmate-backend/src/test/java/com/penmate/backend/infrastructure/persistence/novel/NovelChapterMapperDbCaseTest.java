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
            insertChapter(statement, 1, 5001, null, 0);
        }
    }

    @Test
    void should_order_by_volume_then_chapter_then_row_id_and_place_ungrouped_last() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<NovelChapter> chapters = session.getMapper(NovelChapterMapper.class).findByProjectId(77L);

            assertThat(chapters)
                    .extracting(NovelChapter::getChapterId)
                    .containsExactly(5002L, 5003L, 5004L, 5005L, 5001L);
        }
    }

    private void insertChapter(Statement statement, long id, long chapterId, Long volumeId, int sortOrder) throws Exception {
        String volume = volumeId == null ? "NULL" : volumeId.toString();
        statement.execute("""
                INSERT INTO novel_chapters(
                    id, chapter_id, project_id, volume_id, outline_node_id, title, sort_order, status,
                    word_count, excerpt, content_object_key, content_etag, content_size, content_checksum, content_revision,
                    storage_provider, last_generated_at, created_at, updated_at, deleted_at
                ) VALUES (%d, %d, 77, %s, NULL, 'chapter', %d, 1, 0, NULL, '', NULL, NULL, NULL, 1,
                          's3', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """.formatted(id, chapterId, volume, sortOrder));
    }
}
