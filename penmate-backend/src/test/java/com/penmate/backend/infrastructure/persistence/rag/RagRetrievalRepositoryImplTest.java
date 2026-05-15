package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
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

class RagRetrievalRepositoryImplTest {

    private static final String JDBC_URL = "jdbc:h2:mem:rag_retrieval_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/rag-retrieval-repository";
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUp() throws Exception {
        sqlSessionFactory = buildSqlSessionFactory();
        recreateSchema();
        seedRows();
    }

    @Test
    void should_match_any_user_mentioned_entity_instead_of_literal_pipe_string() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            RagRetrievalMapper mapper = session.getMapper(RagRetrievalMapper.class);
            RagRetrievalRepositoryImpl repository = new RagRetrievalRepositoryImpl(mapper);

            List<RagRetrievedChunk> chunks = repository.searchChunks(
                    990001L,
                    "设定核对",
                    10,
                    42L,
                    3,
                    "林烬|苏砚",
                    "story_bible_query,continuity_checker",
                    "CONTINUITY_CHECK,STORY_BIBLE_QUERY",
                    "AGENT_CONTEXT"
            );

            assertThat(chunks)
                    .extracting(RagRetrievedChunk::getDocumentTitle)
                    .contains("story_bible::hero.identity", "chapter::42::su-yan")
                    .doesNotContain("chapter::42::other");
        }
    }

    @Test
    void should_return_empty_when_no_entity_matches_even_if_pipe_string_itself_is_absent() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            RagRetrievalMapper mapper = session.getMapper(RagRetrievalMapper.class);
            RagRetrievalRepositoryImpl repository = new RagRetrievalRepositoryImpl(mapper);

            List<RagRetrievedChunk> chunks = repository.searchChunks(
                    990001L,
                    "设定核对",
                    10,
                    42L,
                    3,
                    "白檀|阿澈",
                    "story_bible_query,continuity_checker",
                    "CONTINUITY_CHECK,STORY_BIBLE_QUERY",
                    "AGENT_CONTEXT"
            );

            assertThat(chunks).isEmpty();
        }
    }

    @Test
    void should_keep_chapter_and_version_filters_when_matching_multiple_entities() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            RagRetrievalMapper mapper = session.getMapper(RagRetrievalMapper.class);
            RagRetrievalRepositoryImpl repository = new RagRetrievalRepositoryImpl(mapper);

            List<RagRetrievedChunk> chunks = repository.searchChunks(
                    990001L,
                    "设定核对",
                    10,
                    42L,
                    3,
                    "林烬|苏砚",
                    "story_bible_query,continuity_checker",
                    "CONTINUITY_CHECK,STORY_BIBLE_QUERY",
                    "AGENT_CONTEXT"
            );

            assertThat(chunks)
                    .extracting(RagRetrievedChunk::getDocumentTitle)
                    .doesNotContain("chapter::45::stale");
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
        configuration.addMapper(RagRetrievalMapper.class);
        configuration.getTypeAliasRegistry().registerAlias("RagRetrievedChunk", RagRetrievedChunk.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void recreateSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS rag_retrieval_logs");
            statement.execute("DROP TABLE IF EXISTS rag_chunks");
            statement.execute("DROP TABLE IF EXISTS rag_documents");
            statement.execute("DROP TABLE IF EXISTS storage_objects");
        }
        prepareRagMigrationsOnly();
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
                    INSERT INTO rag_documents
                    (id, document_id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag, mime_type, parse_status, index_status, created_at, updated_at, deleted_at)
                    VALUES
                    (1, 880001, 990001, 'story_bible', 'story_bible::hero.identity', 'sb:hero.identity', 'obj-1', NULL, 'text/plain', 'done', 'done', NOW(3), NOW(3), NULL),
                    (2, 880002, 990001, 'chapter', 'chapter::42::su-yan', 'chapter:42', 'obj-2', NULL, 'text/plain', 'done', 'done', NOW(3), NOW(3), NULL),
                    (3, 880003, 990001, 'chapter', 'chapter::45::stale', 'chapter:45', 'obj-3', NULL, 'text/plain', 'done', 'done', NOW(3), NOW(3), NULL),
                    (4, 880004, 990001, 'chapter', 'chapter::42::other', 'chapter:42', 'obj-4', NULL, 'text/plain', 'done', 'done', NOW(3), NOW(3), NULL)
                    """);
            statement.execute("""
                    INSERT INTO rag_chunks
                    (id, chunk_id, project_id, document_id, chunk_no, content_text, token_count, vector_id, vector_store, embedding_provider, embedding_model, embedding_dim, embedding_version, metadata_json, created_at)
                    VALUES
                    (1, 770001, 990001, 880001, 1, 'sourceType=story_bible;sourceId=hero.identity;matchedVersion=3;canon=high;entity=林烬;content=林烬是守夜人见习生;设定核对', 10, 'vec-1', 'milvus', 'openai', 'text-embedding', 1536, 'v1', NULL, NOW(3)),
                    (2, 770002, 990001, 880002, 1, 'sourceType=chapter;sourceId=chapter-42-su-yan;matchedVersion=3;chapter=42;entity=苏砚;content=苏砚察觉城主身份异常;设定核对', 10, 'vec-2', 'milvus', 'openai', 'text-embedding', 1536, 'v1', NULL, NOW(3)),
                    (3, 770003, 990001, 880003, 1, 'sourceType=chapter;sourceId=chapter-45-stale;matchedVersion=2;chapter=45;entity=苏砚;content=苏砚已经知道林烬身世;设定核对', 10, 'vec-3', 'milvus', 'openai', 'text-embedding', 1536, 'v1', NULL, NOW(3)),
                    (4, 770004, 990001, 880004, 1, 'sourceType=chapter;sourceId=chapter-42-other;matchedVersion=3;chapter=42;entity=陌生人;content=其他角色登场;设定核对', 10, 'vec-4', 'milvus', 'openai', 'text-embedding', 1536, 'v1', NULL, NOW(3))
                    """);
        }
    }

    private static void prepareRagMigrationsOnly() throws IOException {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql"),
                migrationDir.resolve("V3__init_storage_and_rag_minimal.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}
