package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
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

    private static final String H2_URL = "jdbc:h2:mem:novel_chapter_order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        DataSource dataSource = new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
                "org.h2.Driver", H2_URL, "sa", ""
        );
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
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE novel_volumes (
                        id BIGINT PRIMARY KEY,
                        volume_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        sort_order INT NOT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE novel_chapters (
                        id BIGINT PRIMARY KEY,
                        chapter_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        volume_id BIGINT NULL,
                        outline_node_id BIGINT NULL,
                        title VARCHAR(200) NOT NULL,
                        sort_order INT NOT NULL,
                        status INT NOT NULL,
                        word_count INT NOT NULL,
                        excerpt VARCHAR(2000) NULL,
                        content_object_key VARCHAR(500) NOT NULL,
                        content_etag VARCHAR(128) NULL,
                        content_size BIGINT NULL,
                        content_checksum VARCHAR(128) NULL,
                        content_revision BIGINT NOT NULL DEFAULT 1,
                        storage_provider VARCHAR(32) NOT NULL,
                        last_generated_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("INSERT INTO novel_volumes VALUES (1, 101, 77, 20, NULL), (2, 102, 77, 10, NULL)");
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
