package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagSourceContent;
import com.penmate.backend.domain.rag.repository.RagSourceCatalogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Repository
public class JdbcRagSourceCatalogRepository implements RagSourceCatalogRepository {
    private final JdbcTemplate jdbc;

    public JdbcRagSourceCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RagSourceContent> listProjectSources(Long projectId) {
        List<RagSourceContent> result = new ArrayList<>();
        result.addAll(jdbc.query("""
                SELECT 'STORY_BIBLE_NODE', n.node_id, n.revision::text, n.title,
                       concat_ws(E'\\n', t.display_name, n.title,
                           NULLIF(string_agg(a.alias, ', ' ORDER BY a.alias), ''), n.summary, n.body_markdown,
                           n.attributes_json::text), NULL, 'md', 'text/markdown'
                FROM story_bibles b
                JOIN story_bible_nodes n ON n.story_bible_id = b.story_bible_id AND n.deleted_at IS NULL AND n.archived_at IS NULL
                JOIN story_bible_node_types t ON t.type_id = n.type_id
                LEFT JOIN story_bible_aliases a ON a.node_id = n.node_id AND a.deleted_at IS NULL
                WHERE b.project_id = ? AND b.deleted_at IS NULL
                GROUP BY n.node_id, n.revision, n.title, t.display_name, n.summary, n.body_markdown, n.attributes_json
                """, (rs, row) -> row(rs), projectId));
        result.addAll(jdbc.query("""
                SELECT 'MANUSCRIPT_CHUNK', chapter_id, content_revision::text, title,
                       content, NULL, 'txt', 'text/plain'
                FROM novel_chapters
                WHERE project_id = ? AND deleted_at IS NULL AND content <> ''
                """, (rs, row) -> row(rs), projectId));
        result.addAll(jdbc.query("""
                SELECT 'KNOWLEDGE_DOCUMENT', document_id, source_revision::text, title,
                       NULL, origin_object_key, file_extension, mime_type
                FROM rag_documents
                WHERE project_id = ? AND deleted_at IS NULL AND parse_status = 'DONE'
                """, (rs, row) -> row(rs), projectId));
        return List.copyOf(result);
    }

    @Override
    public RagSourceContent findKnowledgeDocument(Long projectId, Long documentId) {
        List<RagSourceContent> values = jdbc.query("""
                SELECT 'KNOWLEDGE_DOCUMENT', document_id, source_revision::text, title,
                       NULL, origin_object_key, file_extension, mime_type
                FROM rag_documents WHERE project_id = ? AND document_id = ? AND deleted_at IS NULL
                """, (rs, row) -> row(rs), projectId, documentId);
        return values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public RagSourceContent findSource(Long projectId, String sourceType, Long sourceId) {
        return listProjectSources(projectId).stream()
                .filter(source -> source.sourceType().equals(sourceType) && source.sourceId().equals(sourceId))
                .findFirst()
                .orElse(null);
    }

    private RagSourceContent row(java.sql.ResultSet rs) throws java.sql.SQLException {
        String sourceType = rs.getString(1);
        String rawRevision = rs.getString(3);
        String title = rs.getString(4);
        String inlineContent = rs.getString(5);
        String revision = "KNOWLEDGE_DOCUMENT".equals(sourceType)
                ? rawRevision
                : sha256(String.join("\u001f", sourceType, value(title), value(inlineContent)));
        return new RagSourceContent(sourceType, rs.getLong(2), revision, title,
                inlineContent, rs.getString(6), rs.getString(7), rs.getString(8));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
