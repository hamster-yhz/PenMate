package com.penmate.backend.infrastructure.persistence.storybible;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.domain.storybible.model.StoryBibleSourceRef;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Story Bible MyBatis Mapper。
 * <p>负责按章节边界裁剪项目长期知识条目，供单 Main Orchestrator 的上下文构建读取。</p>
 */
@Mapper
public interface StoryBibleMapper {
 
    @Insert("""
            INSERT INTO story_bible_entries(
                entry_id,
                story_bible_id,
                project_id,
                entry_type,
                entry_key,
                title,
                content,
                canonical_status,
                risk_level,
                source_refs_json,
                valid_from_chapter_id,
                valid_to_chapter_id,
                version_no
            ) VALUES (
                #{entryId},
                #{storyBibleId},
                #{projectId},
                #{entryType},
                #{entryKey},
                #{title},
                #{content},
                #{canonicalStatus},
                #{riskLevel},
                #{sourceRefs, typeHandler=com.penmate.backend.infrastructure.persistence.storybible.StoryBibleMapper$StoryBibleSourceRefListTypeHandler},
                #{validFromChapterId},
                #{validToChapterId},
                #{versionNo}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StoryBibleEntry entry);

    @Update("""
            UPDATE story_bible_entries
            SET story_bible_id = #{storyBibleId},
                entry_type = #{entryType},
                entry_key = #{entryKey},
                title = #{title},
                content = #{content},
                canonical_status = #{canonicalStatus},
                risk_level = #{riskLevel},
                source_refs_json = #{sourceRefs, typeHandler=com.penmate.backend.infrastructure.persistence.storybible.StoryBibleMapper$StoryBibleSourceRefListTypeHandler},
                valid_from_chapter_id = #{validFromChapterId},
                valid_to_chapter_id = #{validToChapterId},
                version_no = #{versionNo}
            WHERE project_id = #{projectId}
              AND entry_id = #{entryId}
              AND deleted_at IS NULL
            """)
    int update(StoryBibleEntry entry);

    @Select("""
            SELECT id,
                   story_bible_id AS storyBibleId,
                   project_id AS projectId,
                   title,
                   description,
                   active_version_no AS activeVersionNo,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM story_bibles
            WHERE project_id = #{projectId}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    StoryBible findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id,
                   entry_id,
                   story_bible_id,
                   project_id,
                   entry_type,
                   entry_key,
                   title,
                   content,
                   canonical_status,
                   risk_level,
                   CAST(source_refs_json AS VARCHAR) AS source_refs_json_text,
                   valid_from_chapter_id,
                   valid_to_chapter_id,
                   version_no,
                   created_at,
                   updated_at,
                   deleted_at
            FROM story_bible_entries
            WHERE project_id = #{projectId}
              AND entry_id = #{entryId}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    @Results(id = "storyBibleEntrySingleResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "entry_id", property = "entryId"),
            @Result(column = "story_bible_id", property = "storyBibleId"),
            @Result(column = "project_id", property = "projectId"),
            @Result(column = "entry_type", property = "entryType"),
            @Result(column = "entry_key", property = "entryKey"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "canonical_status", property = "canonicalStatus"),
            @Result(column = "risk_level", property = "riskLevel"),
            @Result(column = "source_refs_json_text", property = "sourceRefs", javaType = List.class, typeHandler = StoryBibleSourceRefListTypeHandler.class),
            @Result(column = "valid_from_chapter_id", property = "validFromChapterId", jdbcType = JdbcType.BIGINT),
            @Result(column = "valid_to_chapter_id", property = "validToChapterId", jdbcType = JdbcType.BIGINT),
            @Result(column = "version_no", property = "versionNo"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "deleted_at", property = "deletedAt")
    })
    StoryBibleEntry findByEntryId(@Param("projectId") Long projectId,
                                  @Param("entryId") Long entryId);
 
    @Select("""
            SELECT entry.id,
                   entry.entry_id,
                   entry.story_bible_id,
                   entry.project_id,
                   entry.entry_type,
                   entry.entry_key,
                   entry.title,
                   entry.content,
                   entry.canonical_status,
                   entry.risk_level,
                   CAST(entry.source_refs_json AS VARCHAR) AS source_refs_json_text,
                   entry.valid_from_chapter_id,
                   entry.valid_to_chapter_id,
                   entry.version_no,
                   entry.created_at,
                   entry.updated_at,
                   entry.deleted_at
            FROM story_bible_entries entry
            INNER JOIN story_bibles sb
                    ON sb.story_bible_id = entry.story_bible_id
                   AND sb.project_id = entry.project_id
                   AND sb.deleted_at IS NULL
            WHERE entry.project_id = #{projectId}
              AND entry.canonical_status IN ('CANON', 'PROPOSED')
              AND entry.deleted_at IS NULL
              AND entry.version_no <= sb.active_version_no
              AND (entry.valid_from_chapter_id IS NULL OR entry.valid_from_chapter_id <= #{chapterId})
              AND (entry.valid_to_chapter_id IS NULL OR entry.valid_to_chapter_id >= #{chapterId})
            ORDER BY CASE entry.canonical_status WHEN 'CANON' THEN 0 WHEN 'PROPOSED' THEN 1 ELSE 9 END,
                     entry.version_no ASC,
                     entry.id ASC
            """)
    @Results(id = "storyBibleEntryResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "entry_id", property = "entryId"),
            @Result(column = "story_bible_id", property = "storyBibleId"),
            @Result(column = "project_id", property = "projectId"),
            @Result(column = "entry_type", property = "entryType"),
            @Result(column = "entry_key", property = "entryKey"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "canonical_status", property = "canonicalStatus"),
            @Result(column = "risk_level", property = "riskLevel"),
            @Result(column = "source_refs_json_text", property = "sourceRefs", javaType = List.class, typeHandler = StoryBibleSourceRefListTypeHandler.class),
            @Result(column = "valid_from_chapter_id", property = "validFromChapterId", jdbcType = JdbcType.BIGINT),
            @Result(column = "valid_to_chapter_id", property = "validToChapterId", jdbcType = JdbcType.BIGINT),
            @Result(column = "version_no", property = "versionNo"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "deleted_at", property = "deletedAt")
    })
    List<StoryBibleEntry> findActiveEntries(@Param("projectId") Long projectId, @Param("chapterId") Long chapterId);

    @Update("""
            UPDATE story_bible_entries
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId}
              AND entry_id = #{entryId}
              AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId,
                   @Param("entryId") Long entryId);

    /**
     * Story Bible 来源引用 JSON 列表类型处理器。
     * <p>当前最小实现只解析测试所需的 refType/refId/note 结构。</p>
     */
    class StoryBibleSourceRefListTypeHandler extends BaseTypeHandler<List<StoryBibleSourceRef>> {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, List<StoryBibleSourceRef> parameter, JdbcType jdbcType)
                throws SQLException {
            try {
                ps.setString(i, objectMapper.writeValueAsString(parameter));
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to serialize story bible source refs", e);
            }
        }

        @Override
        public List<StoryBibleSourceRef> getNullableResult(ResultSet rs, String columnName) throws SQLException {
            return parse(rs.getString(columnName));
        }

        @Override
        public List<StoryBibleSourceRef> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            return parse(rs.getString(columnIndex));
        }

        @Override
        public List<StoryBibleSourceRef> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            return parse(cs.getString(columnIndex));
        }

        private List<StoryBibleSourceRef> parse(String json) throws SQLException {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                if (root != null && root.isTextual()) {
                    root = objectMapper.readTree(root.asText());
                }
                if (!root.isArray()) {
                    return List.of();
                }
                List<StoryBibleSourceRef> refs = new ArrayList<>();
                for (JsonNode item : root) {
                    StoryBibleSourceRef ref = new StoryBibleSourceRef();
                    ref.setRefType(item.path("refType").asText(null));
                    if (!item.path("refId").isMissingNode() && !item.path("refId").isNull()) {
                        ref.setRefId(item.path("refId").asLong());
                    }
                    ref.setNote(item.path("note").asText(null));
                    refs.add(ref);
                }
                return refs;
            } catch (Exception e) {
                throw new SQLException("Failed to parse story bible source refs json", e);
            }
        }
    }
}
