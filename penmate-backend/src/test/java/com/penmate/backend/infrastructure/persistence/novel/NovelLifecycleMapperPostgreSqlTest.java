package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelCoverUploadSession;
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
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NovelLifecycleMapperPostgreSqlTest {

    private static final long PROJECT_ID = 931001L;
    private static final long OWNER_ID = 931002L;
    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("novel_lifecycle_mapper");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NovelCoverUploadMapper.class);
        configuration.addMapper(NovelProjectMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetRows() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM novel_cover_upload_sessions WHERE project_id = " + PROJECT_ID);
            statement.execute("DELETE FROM novel_chapters WHERE project_id = " + PROJECT_ID);
            statement.execute("DELETE FROM novel_volumes WHERE project_id = " + PROJECT_ID);
            statement.execute("DELETE FROM novel_projects WHERE project_id = " + PROJECT_ID);
        }
    }

    @Test
    void persists_processes_and_applies_a_cover_upload() throws Exception {
        insertProject(false);
        NovelCoverUploadSession upload = coverUpload(931101L, "PENDING");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            NovelCoverUploadMapper mapper = session.getMapper(NovelCoverUploadMapper.class);
            assertThat(mapper.insert(upload)).isOne();
            assertThat(upload.getId()).isNotNull();
            assertThat(mapper.findExpiredPending(Instant.now().plus(Duration.ofHours(2))))
                    .extracting(NovelCoverUploadSession::getUploadId)
                    .contains(upload.getUploadId());
            assertThat(mapper.setPendingUpload(PROJECT_ID, OWNER_ID, upload.getUploadId())).isOne();
            assertThat(mapper.markProcessing(
                    upload.getUploadId(), 0.1, 0.2, 0.6, 0.9,
                    1200, 1800, "covers/display.webp", "covers/thumb.webp")).isOne();

            NovelCoverUploadSession processing = mapper.findById(upload.getUploadId());
            assertThat(processing.getStatus()).isEqualTo("PROCESSING");
            assertThat(processing.getCropX()).isEqualTo(0.1);
            assertThat(processing.getImageWidth()).isEqualTo(1200);

            assertThat(mapper.markCompleted(upload.getUploadId())).isOne();
            assertThat(mapper.applyCover(
                    PROJECT_ID, upload.getUploadId(), upload.getOriginalObjectKey(),
                    "covers/display.webp", "covers/thumb.webp")).isOne();
        }

        assertThat(queryString("SELECT cover_display_object_key FROM novel_projects WHERE project_id = " + PROJECT_ID))
                .isEqualTo("covers/display.webp");
        assertThat(queryString("SELECT status FROM novel_cover_upload_sessions WHERE upload_id = 931101"))
                .isEqualTo("COMPLETED");
    }

    @Test
    void permanently_deletes_a_project_and_its_cover_and_chapter_rows() throws Exception {
        insertProject(true);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO novel_volumes(volume_id, project_id, title) VALUES (931201, " + PROJECT_ID + ", 'Volume')");
            statement.execute("""
                    INSERT INTO novel_chapters(chapter_id, project_id, volume_id, title, content)
                    VALUES (931301, %d, 931201, 'Chapter', 'Draft')
                    """.formatted(PROJECT_ID));
        }
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.getMapper(NovelCoverUploadMapper.class).insert(coverUpload(931102L, "COMPLETED"));
            NovelProjectMapper mapper = session.getMapper(NovelProjectMapper.class);
            Instant deletedBefore = Instant.now().minus(Duration.ofDays(30));
            assertThat(mapper.findExpiredDeletedProjectIds(deletedBefore)).contains(PROJECT_ID);
            assertThat(mapper.lockDeletedProject(PROJECT_ID, OWNER_ID, deletedBefore)).isNotNull();
            assertThat(mapper.findProjectObjectKeys(PROJECT_ID))
                    .contains("covers/original.png", "covers/old-display.webp", "covers/old-thumb.webp");
            int deleted = mapper.purgeDeleted(PROJECT_ID, OWNER_ID, deletedBefore);
            assertThat(deleted).isOne();
        }

        assertThat(count("novel_projects")).isZero();
        assertThat(count("novel_volumes")).isZero();
        assertThat(count("novel_chapters")).isZero();
        assertThat(count("novel_cover_upload_sessions")).isZero();
    }

    private void insertProject(boolean deleted) throws Exception {
        String deletedAt = deleted ? "CURRENT_TIMESTAMP - INTERVAL '31 days'" : "NULL";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO novel_projects(
                        project_id, owner_user_id, title, cover_original_object_key,
                        cover_display_object_key, cover_thumbnail_object_key, deleted_at)
                    VALUES (%d, %d, 'Project', 'covers/original.png',
                            'covers/old-display.webp', 'covers/old-thumb.webp', %s)
                    """.formatted(PROJECT_ID, OWNER_ID, deletedAt));
        }
    }

    private NovelCoverUploadSession coverUpload(long uploadId, String status) {
        NovelCoverUploadSession upload = new NovelCoverUploadSession();
        upload.setUploadId(uploadId);
        upload.setProjectId(PROJECT_ID);
        upload.setOwnerUserId(OWNER_ID);
        upload.setOperationType("UPLOAD");
        upload.setOriginalFilename("cover.png");
        upload.setDeclaredMimeType("image/png");
        upload.setExpectedSize(1024L);
        upload.setOriginalObjectKey("covers/original.png");
        upload.setUploadTokenHash("token-hash");
        upload.setStatus(status);
        upload.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return upload;
    }

    private long count(String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + table + " WHERE project_id = " + PROJECT_ID)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
