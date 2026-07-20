package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagEmbeddingSpace;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPgVectorPostgreSqlTest {
    @Test
    void provisionsHnswPartitionAndSearchesOnlyActiveBuild() {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("rag_pgvector");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcRagIndexRepository repository = new JdbcRagIndexRepository(jdbc);
        long projectId = 91001L;
        long modelId = 92001L;
        long spaceId = 93001L;
        long buildId = 94001L;
        RagEmbeddingSpace space = new RagEmbeddingSpace(spaceId, "a".repeat(64), 1L,
                "OPENAI_EMBEDDINGS", "https://example.com/v1", "embed-test", 3,
                "COSINE", "VECTOR", "rag_vec_f32_93001", "PROVISIONING");

        jdbc.update("INSERT INTO novel_projects(project_id, owner_user_id, title) VALUES (?, ?, ?)", projectId, 1L, "Test");
        jdbc.update("""
                INSERT INTO project_ai_configurations(project_ai_config_id, project_id, embedding_model_config_id,
                    story_bible_routing_mode, index_status)
                VALUES (?, ?, ?, 'LLM_SELECTOR', 'REINDEX_REQUIRED')
                """, 95001L, projectId, modelId);
        assertThat(repository.insertSpace(space)).isEqualTo(1);
        repository.provisionSpace(space);
        RagEmbeddingSpace activeSpace = repository.findSpace(space.identityHash());
        assertThat(activeSpace.spaceStatus()).isEqualTo("ACTIVE");

        repository.createBuild(buildId, projectId, modelId, spaceId, 1, 11);
        repository.insertSource(96001L, buildId, projectId, "KNOWLEDGE_DOCUMENT", 97001L,
                "1", "Document", "b".repeat(64), 11, 1);
        repository.insertChunks(96001L, buildId, projectId, spaceId, "KNOWLEDGE_DOCUMENT", 97001L,
                List.of(new RagIndexRepository.ChunkWrite(98001L, 0, "hello world", "c".repeat(64), "{}")));
        repository.insertVectors(activeSpace, buildId, projectId,
                List.of(new RagIndexRepository.VectorWrite(99001L, 98001L, new float[]{1f, 0f, 0f})));
        repository.activateBuild(projectId, buildId, 1, 1);

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var hits = transaction.execute(status -> repository.search(projectId, activeSpace,
                new float[]{1f, 0f, 0f}, List.of("KNOWLEDGE_DOCUMENT"), 30, 8, 3, 100, null));

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().getSourceId()).isEqualTo(97001L);
        assertThat(hits.getFirst().getContentText()).isEqualTo("hello world");
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "rag_vec_f32_93001"))
                .anyMatch(index -> index.contains("hnsw"));
    }
}
