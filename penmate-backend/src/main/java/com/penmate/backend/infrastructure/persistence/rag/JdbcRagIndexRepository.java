package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagEmbeddingSpace;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Repository
public class JdbcRagIndexRepository implements RagIndexRepository {
    private final JdbcTemplate jdbc;

    public JdbcRagIndexRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RagEmbeddingSpace findSpace(String identityHash) {
        List<RagEmbeddingSpace> rows = jdbc.query("""
                SELECT embedding_space_id, identity_hash, provider_id, protocol_code, normalized_base_url,
                       model_name, embedding_dimension, distance_metric, storage_type, partition_name, space_status
                FROM rag_embedding_spaces WHERE identity_hash = ? AND deleted_at IS NULL
                """, (rs, row) -> new RagEmbeddingSpace(rs.getLong(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getString(11)), identityHash);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public RagEmbeddingSpace findActiveSpaceForProject(Long projectId) {
        List<RagEmbeddingSpace> rows = jdbc.query("""
                SELECT s.embedding_space_id, s.identity_hash, s.provider_id, s.protocol_code, s.normalized_base_url,
                       s.model_name, s.embedding_dimension, s.distance_metric, s.storage_type, s.partition_name, s.space_status
                FROM project_ai_configurations p
                JOIN rag_index_builds b ON b.index_build_id = p.active_index_build_id AND b.build_status = 'ACTIVE'
                JOIN rag_embedding_spaces s ON s.embedding_space_id = b.embedding_space_id AND s.space_status = 'ACTIVE'
                WHERE p.project_id = ? AND p.index_status = 'READY'
                """, (rs, row) -> new RagEmbeddingSpace(rs.getLong(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getString(11)), projectId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public int insertSpace(RagEmbeddingSpace space) {
        try {
            return jdbc.update("""
                    INSERT INTO rag_embedding_spaces(embedding_space_id, identity_hash, provider_id, protocol_code,
                        normalized_base_url, model_name, embedding_dimension, distance_metric, storage_type,
                        partition_name, space_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROVISIONING')
                    """, space.embeddingSpaceId(), space.identityHash(), space.providerId(), space.protocolCode(),
                    space.normalizedBaseUrl(), space.modelName(), space.embeddingDimension(), space.distanceMetric(),
                    space.storageType(), space.partitionName());
        } catch (DataIntegrityViolationException duplicate) {
            return 0;
        }
    }

    @Override
    @Transactional
    public void provisionSpace(RagEmbeddingSpace space) {
        validateSpace(space);
        String table = identifier(space.partitionName());
        String parent = "VECTOR".equals(space.storageType()) ? "rag_vectors_f32" : "rag_vectors_f16";
        String type = "VECTOR".equals(space.storageType()) ? "vector" : "halfvec";
        String operatorClass = switch (space.distanceMetric()) {
            case "COSINE" -> type + "_cosine_ops";
            case "INNER_PRODUCT" -> type + "_ip_ops";
            case "L2" -> type + "_l2_ops";
            default -> throw new IllegalArgumentException("Unsupported distance metric");
        };
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " PARTITION OF " + parent
                + " FOR VALUES IN (" + space.embeddingSpaceId() + ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS " + identifier("idx_" + table + "_hnsw") + " ON " + table
                + " USING hnsw ((embedding::" + type + "(" + space.embeddingDimension() + ")) "
                + operatorClass + ") WITH (m = 16, ef_construction = 64)");
        jdbc.update("""
                UPDATE rag_embedding_spaces SET space_status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP(3)
                WHERE embedding_space_id = ? AND deleted_at IS NULL
                """, space.embeddingSpaceId());
    }

    @Override
    public Long createBuild(Long buildId, Long projectId, Long modelConfigId, Long embeddingSpaceId,
                            int sourceCount, long characterCount) {
        jdbc.update("""
                INSERT INTO rag_index_builds(index_build_id, project_id, model_config_id, embedding_space_id,
                    build_status, source_count, estimated_character_count, started_at)
                VALUES (?, ?, ?, ?, 'BUILDING', ?, ?, CURRENT_TIMESTAMP(3))
                """, buildId, projectId, modelConfigId, embeddingSpaceId, sourceCount, characterCount);
        return buildId;
    }

    @Override
    public Long insertSource(Long sourceIndexId, Long buildId, Long projectId, String sourceType, Long sourceId,
                             String revision, String title, String checksum, long characterCount, int chunkCount) {
        jdbc.update("""
                INSERT INTO rag_index_sources(source_index_id, index_build_id, project_id, source_type, source_id,
                    source_revision, source_title, source_status, content_checksum, character_count, chunk_count, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'STAGED', ?, ?, ?, FALSE)
                ON CONFLICT (index_build_id, source_type, source_id, source_revision) DO NOTHING
                """, sourceIndexId, buildId, projectId, sourceType, sourceId, revision, title, checksum,
                characterCount, chunkCount);
        return sourceIndexId;
    }

    @Override
    @Transactional
    public void resetStagedSource(Long buildId, String sourceType, Long sourceId, String revision) {
        List<Long> sourceIndexes = jdbc.queryForList("""
                SELECT source_index_id FROM rag_index_sources
                WHERE index_build_id = ? AND source_type = ? AND source_id = ? AND source_revision = ? AND active = FALSE
                """, Long.class, buildId, sourceType, sourceId, revision);
        for (Long sourceIndexId : sourceIndexes) {
            jdbc.update("DELETE FROM rag_vectors_f32 WHERE chunk_id IN (SELECT chunk_id FROM rag_chunks WHERE source_index_id = ?)", sourceIndexId);
            jdbc.update("DELETE FROM rag_vectors_f16 WHERE chunk_id IN (SELECT chunk_id FROM rag_chunks WHERE source_index_id = ?)", sourceIndexId);
            jdbc.update("DELETE FROM rag_chunks WHERE source_index_id = ?", sourceIndexId);
            jdbc.update("DELETE FROM rag_index_sources WHERE source_index_id = ? AND active = FALSE", sourceIndexId);
        }
    }

    @Override
    public void insertChunks(Long sourceIndexId, Long buildId, Long projectId, Long embeddingSpaceId,
                             String sourceType, Long sourceId, List<ChunkWrite> chunks) {
        for (ChunkWrite chunk : chunks) {
            jdbc.update("""
                    INSERT INTO rag_chunks(chunk_id, source_index_id, index_build_id, project_id, embedding_space_id,
                        source_type, source_id, chunk_no, content_text, character_count, content_hash, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (source_index_id, chunk_no) DO NOTHING
                    """, chunk.chunkId(), sourceIndexId, buildId, projectId, embeddingSpaceId, sourceType, sourceId,
                    chunk.chunkNo(), chunk.content(), chunk.content().length(), chunk.contentHash(), chunk.metadataJson());
        }
    }

    @Override
    public void insertVectors(RagEmbeddingSpace space, Long buildId, Long projectId, List<VectorWrite> vectors) {
        validateSpace(space);
        String table = identifier(space.partitionName());
        String type = "VECTOR".equals(space.storageType()) ? "vector" : "halfvec";
        String sql = "INSERT INTO " + table
                + "(embedding_space_id, vector_id, chunk_id, project_id, index_build_id, embedding) "
                + "VALUES (?, ?, ?, ?, ?, ?::" + type + "(" + space.embeddingDimension() + ")) "
                + "ON CONFLICT (embedding_space_id, chunk_id) DO NOTHING";
        for (VectorWrite vector : vectors) {
            if (vector.embedding().length != space.embeddingDimension()) throw new IllegalArgumentException("Embedding dimension changed during build");
            jdbc.update(sql, space.embeddingSpaceId(), vector.vectorId(), vector.chunkId(), projectId, buildId,
                    vectorLiteral(vector.embedding()));
        }
    }

    @Override
    @Transactional
    public void activateBuild(Long projectId, Long buildId, int sourceCount, int chunkCount) {
        jdbc.update("""
                UPDATE rag_index_builds SET build_status = 'SUPERSEDED', finished_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ? AND build_status = 'ACTIVE' AND index_build_id <> ?
                """, projectId, buildId);
        jdbc.update("""
                UPDATE rag_index_sources SET active = TRUE, source_status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3) WHERE index_build_id = ?
                """, buildId);
        if (jdbc.update("""
                UPDATE rag_index_builds SET build_status = 'ACTIVE', completed_source_count = ?, chunk_count = ?,
                    embedded_chunk_count = ?, activated_at = CURRENT_TIMESTAMP(3), finished_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3) WHERE index_build_id = ? AND build_status = 'BUILDING'
                """, sourceCount, chunkCount, chunkCount, buildId) != 1) {
            throw new IllegalStateException("RAG build is no longer activatable");
        }
        if (jdbc.update("""
                UPDATE project_ai_configurations SET active_index_build_id = ?, index_status = 'READY',
                    last_error_code = NULL, last_error_message = NULL, updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ? AND embedding_model_config_id =
                    (SELECT model_config_id FROM rag_index_builds WHERE index_build_id = ?)
                """, buildId, projectId, buildId) != 1) {
            throw new IllegalStateException("Project Embedding binding changed during build");
        }
    }

    @Override
    @Transactional
    public void failBuild(Long projectId, Long buildId, String errorCode, String errorMessage) {
        jdbc.update("""
                UPDATE rag_index_builds SET build_status = 'FAILED', last_error_code = ?, last_error_message = ?,
                    finished_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
                WHERE index_build_id = ? AND build_status = 'BUILDING'
                """, errorCode, truncate(errorMessage, 500), buildId);
        jdbc.update("""
                UPDATE project_ai_configurations SET index_status = 'REINDEX_REQUIRED', active_index_build_id = NULL,
                    last_error_code = ?, last_error_message = ?, updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ?
                """, errorCode, truncate(errorMessage, 500), projectId);
    }

    @Override
    @Transactional
    public void activateSource(Long buildId, String sourceType, Long sourceId, Long sourceIndexId) {
        jdbc.update("""
                UPDATE rag_index_sources SET active = FALSE, source_status = 'SUPERSEDED',
                    superseded_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
                WHERE index_build_id = ? AND source_type = ? AND source_id = ? AND active = TRUE
                """, buildId, sourceType, sourceId);
        if (jdbc.update("""
                UPDATE rag_index_sources SET active = TRUE, source_status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3) WHERE source_index_id = ? AND index_build_id = ?
                """, sourceIndexId, buildId) != 1) throw new IllegalStateException("Failed to activate indexed source");
    }

    @Override
    @Transactional
    public void removeSource(Long projectId, String sourceType, Long sourceId) {
        jdbc.update("""
                UPDATE rag_index_sources SET active = FALSE, source_status = 'DELETED',
                    superseded_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
                WHERE project_id = ? AND source_type = ? AND source_id = ? AND active = TRUE
                """, projectId, sourceType, sourceId);
    }

    @Override
    @Transactional
    public void deleteBuild(Long buildId) {
        jdbc.update("DELETE FROM rag_vectors_f32 WHERE index_build_id = ?", buildId);
        jdbc.update("DELETE FROM rag_vectors_f16 WHERE index_build_id = ?", buildId);
        jdbc.update("DELETE FROM rag_chunks WHERE index_build_id = ?", buildId);
        jdbc.update("DELETE FROM rag_index_sources WHERE index_build_id = ?", buildId);
        jdbc.update("DELETE FROM rag_index_builds WHERE index_build_id = ? AND build_status <> 'ACTIVE'", buildId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RagRetrievedChunk> search(Long projectId, RagEmbeddingSpace space, float[] queryVector,
                                          List<String> sourceTypes, int candidates, int topK,
                                          int maxPerSource, int efSearch, Double threshold) {
        validateSpace(space);
        if (queryVector.length != space.embeddingDimension()) throw new IllegalArgumentException("Query vector dimension mismatch");
        jdbc.execute("SET LOCAL hnsw.ef_search = " + Math.max(1, Math.min(1000, efSearch)));
        String table = identifier(space.partitionName());
        String type = "VECTOR".equals(space.storageType()) ? "vector" : "halfvec";
        String operator = switch (space.distanceMetric()) {
            case "COSINE" -> "<=>";
            case "INNER_PRODUCT" -> "<#>";
            case "L2" -> "<->";
            default -> throw new IllegalArgumentException("Unsupported distance metric");
        };
        List<String> filters = sourceTypes == null || sourceTypes.isEmpty()
                ? List.of("MANUSCRIPT_CHUNK", "KNOWLEDGE_DOCUMENT") : sourceTypes;
        String placeholders = String.join(",", filters.stream().map(ignored -> "?").toList());
        String distance = "(v.embedding::" + type + "(" + space.embeddingDimension() + ") " + operator
                + " ?::" + type + "(" + space.embeddingDimension() + "))";
        String sql = """
                WITH nearest AS (
                    SELECT c.chunk_id, c.source_type, c.source_id, s.source_title, c.chunk_no, c.content_text,
                           %s AS distance
                    FROM %s v
                    JOIN rag_chunks c ON c.chunk_id = v.chunk_id AND c.index_build_id = v.index_build_id
                    JOIN rag_index_sources s ON s.source_index_id = c.source_index_id AND s.active = TRUE
                    JOIN project_ai_configurations p ON p.project_id = c.project_id
                        AND p.active_index_build_id = c.index_build_id AND p.index_status = 'READY'
                    WHERE c.project_id = ? AND c.source_type IN (%s)
                    ORDER BY %s
                    LIMIT ?
                ), ranked AS (
                    SELECT *, row_number() OVER (PARTITION BY source_type, source_id ORDER BY distance) AS source_rank
                    FROM nearest
                )
                SELECT source_type, source_id, source_title, chunk_no, content_text, distance
                FROM ranked WHERE source_rank <= ? AND (?::double precision IS NULL OR distance <= ?::double precision)
                ORDER BY distance LIMIT ?
                """.formatted(distance, table, placeholders, distance);
        List<Object> args = new ArrayList<>();
        String literal = vectorLiteral(queryVector);
        args.add(literal);
        args.add(projectId);
        args.addAll(filters);
        args.add(literal);
        args.add(candidates);
        args.add(maxPerSource);
        args.add(threshold);
        args.add(threshold);
        args.add(topK);
        return jdbc.query(sql, (rs, row) -> {
            RagRetrievedChunk chunk = new RagRetrievedChunk();
            chunk.setSourceType(rs.getString(1));
            chunk.setSourceId(rs.getLong(2));
            chunk.setSourceTitle(rs.getString(3));
            chunk.setDocumentId(rs.getLong(2));
            chunk.setDocumentTitle(rs.getString(3));
            chunk.setChunkNo(rs.getInt(4));
            chunk.setContentText(rs.getString(5));
            chunk.setDistance(rs.getDouble(6));
            return chunk;
        }, args.toArray());
    }

    private void validateSpace(RagEmbeddingSpace space) {
        Objects.requireNonNull(space, "space");
        if (space.embeddingDimension() < 1 || space.embeddingDimension() > 4000) throw new IllegalArgumentException("Invalid Embedding dimension");
        identifier(space.partitionName());
    }

    private String identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Unsafe PostgreSQL identifier");
        return value;
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder result = new StringBuilder(vector.length * 10).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) result.append(',');
            result.append(Float.toString(vector[index]));
        }
        return result.append(']').toString();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
