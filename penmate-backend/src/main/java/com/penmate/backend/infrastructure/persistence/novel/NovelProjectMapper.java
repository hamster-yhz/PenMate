package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelProject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * NovelProjectMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface NovelProjectMapper {

    @Select("""
            SELECT p.id, p.project_id, p.owner_user_id, p.title, p.summary, p.genre, p.custom_genre, p.tags,
                   p.cover_original_object_key, p.cover_display_object_key, p.cover_thumbnail_object_key,
                   p.cover_pending_upload_id,
                   p.status, p.structure_revision, p.created_at, p.updated_at, p.deleted_at,
                   COALESCE((SELECT SUM(c.word_count) FROM novel_chapters c
                             WHERE c.project_id = p.project_id AND c.deleted_at IS NULL), 0) AS total_words,
                   COALESCE((SELECT COUNT(*) FROM novel_chapters c
                             WHERE c.project_id = p.project_id AND c.deleted_at IS NULL), 0) AS total_chapters
            FROM novel_projects p
            WHERE p.deleted_at IS NULL
            ORDER BY p.updated_at DESC
            """)
    @Results(id = "projectResult", value = {
            @Result(column = "tags", property = "tags", typeHandler = com.penmate.backend.infrastructure.persistence.support.StringListArrayTypeHandler.class)
    })
    List<NovelProject> findAll();

    @Select("""
            SELECT p.id, p.project_id, p.owner_user_id, p.title, p.summary, p.genre, p.custom_genre, p.tags,
                   p.cover_original_object_key, p.cover_display_object_key, p.cover_thumbnail_object_key,
                   p.cover_pending_upload_id,
                   p.status, p.structure_revision, p.created_at, p.updated_at, p.deleted_at,
                   COALESCE((SELECT SUM(c.word_count) FROM novel_chapters c
                             WHERE c.project_id = p.project_id AND c.deleted_at IS NULL), 0) AS total_words,
                   COALESCE((SELECT COUNT(*) FROM novel_chapters c
                             WHERE c.project_id = p.project_id AND c.deleted_at IS NULL), 0) AS total_chapters
            FROM novel_projects p
            WHERE p.owner_user_id = #{ownerUserId} AND p.deleted_at IS NOT NULL
            ORDER BY p.deleted_at DESC
            """)
    @ResultMap("projectResult")
    List<NovelProject> findDeletedByOwner(@Param("ownerUserId") Long ownerUserId);

    @Select("""
            SELECT id, project_id, owner_user_id, title, summary, genre, custom_genre, tags,
                   cover_original_object_key, cover_display_object_key, cover_thumbnail_object_key,
                   cover_pending_upload_id,
                   status, structure_revision, created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE project_id = #{projectId} AND owner_user_id = #{ownerUserId} AND deleted_at IS NOT NULL
            """)
    @ResultMap("projectResult")
    NovelProject findDeletedByProjectIdAndOwner(@Param("projectId") Long projectId,
                                                @Param("ownerUserId") Long ownerUserId);

    @Select("""
            SELECT id, project_id, owner_user_id, title, summary, genre, custom_genre, tags,
                   cover_original_object_key, cover_display_object_key, cover_thumbnail_object_key,
                   cover_pending_upload_id,
                   status, structure_revision, created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE project_id = #{projectId}
              AND deleted_at IS NOT NULL
              AND (#{ownerUserId}::BIGINT IS NULL OR owner_user_id = #{ownerUserId})
              AND (#{deletedBefore}::TIMESTAMPTZ IS NULL OR deleted_at < #{deletedBefore})
            FOR UPDATE
            """)
    @ResultMap("projectResult")
    NovelProject lockDeletedProject(@Param("projectId") Long projectId,
                                    @Param("ownerUserId") Long ownerUserId,
                                    @Param("deletedBefore") Instant deletedBefore);

    @Select("""
            SELECT project_id
            FROM novel_projects
            WHERE deleted_at < #{deletedBefore}
            ORDER BY deleted_at
            LIMIT 100
            """)
    List<Long> findExpiredDeletedProjectIds(@Param("deletedBefore") Instant deletedBefore);

    @Select("""
            SELECT DISTINCT object_key
            FROM (
                SELECT cover_original_object_key AS object_key FROM novel_projects WHERE project_id = #{projectId}
                UNION ALL SELECT cover_display_object_key FROM novel_projects WHERE project_id = #{projectId}
                UNION ALL SELECT cover_thumbnail_object_key FROM novel_projects WHERE project_id = #{projectId}
                UNION ALL SELECT original_object_key FROM novel_cover_upload_sessions WHERE project_id = #{projectId}
                UNION ALL SELECT display_object_key FROM novel_cover_upload_sessions WHERE project_id = #{projectId}
                UNION ALL SELECT thumbnail_object_key FROM novel_cover_upload_sessions WHERE project_id = #{projectId}
                UNION ALL SELECT object_key FROM rag_upload_sessions WHERE project_id = #{projectId}
                UNION ALL SELECT origin_object_key FROM rag_documents WHERE project_id = #{projectId}
                UNION ALL
                    SELECT e.snapshot_object_key
                    FROM agent_context_epochs e
                    JOIN agent_sessions s ON s.session_id = e.session_id
                    WHERE s.project_id = #{projectId}
                UNION ALL
                    SELECT c.state_object_key
                    FROM agent_checkpoints c
                    JOIN agent_runs r ON r.run_id = c.run_id
                    WHERE r.project_id = #{projectId}
                UNION ALL
                    SELECT a.object_key
                    FROM agent_event_archives a
                    JOIN agent_runs r ON r.run_id = a.run_id
                    WHERE r.project_id = #{projectId}
            ) project_objects
            WHERE object_key IS NOT NULL AND btrim(object_key) <> ''
            """)
    List<String> findProjectObjectKeys(@Param("projectId") Long projectId);

    @Select("""
            SELECT project_id FROM novel_projects
            WHERE owner_user_id = #{ownerUserId}
            ORDER BY id
            """)
    List<Long> findProjectIdsByOwner(@Param("ownerUserId") Long ownerUserId);

    @Select("""
            SELECT id, project_id, owner_user_id, title, summary, genre, custom_genre, tags,
                   cover_original_object_key, cover_display_object_key, cover_thumbnail_object_key,
                   cover_pending_upload_id,
                   status, structure_revision,
                   created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    @ResultMap("projectResult")
    NovelProject findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, project_id, owner_user_id, title, summary, genre, custom_genre, tags,
                   cover_original_object_key, cover_display_object_key, cover_thumbnail_object_key,
                   cover_pending_upload_id,
                   status, structure_revision, created_at, updated_at, deleted_at
            FROM novel_projects
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    @ResultMap("projectResult")
    NovelProject lockByProjectId(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO novel_projects(project_id, owner_user_id, title, summary, genre, custom_genre, tags,
                                       status, structure_revision)
            VALUES(#{projectId}, #{ownerUserId}, #{title}, #{summary}, #{genre}, #{customGenre},
                   #{tags,typeHandler=com.penmate.backend.infrastructure.persistence.support.StringListArrayTypeHandler},
                   #{status}, #{structureRevision})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelProject project);

    @Update("""
            UPDATE novel_projects
            SET title = #{title}, summary = #{summary}, genre = #{genre}, custom_genre = #{customGenre},
                tags = #{tags,typeHandler=com.penmate.backend.infrastructure.persistence.support.StringListArrayTypeHandler},
                status = #{status}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(NovelProject project);

    @Update("""
            UPDATE novel_projects
            SET structure_revision = structure_revision + 1
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            """)
    int incrementStructureRevision(@Param("projectId") Long projectId);

    @Update("""
            UPDATE novel_projects
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId}
              AND owner_user_id = #{ownerUserId}
              AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId, @Param("ownerUserId") Long ownerUserId);

    @Update("""
            UPDATE novel_projects
            SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId}
              AND owner_user_id = #{ownerUserId}
              AND deleted_at IS NOT NULL
            """)
    int restore(@Param("projectId") Long projectId, @Param("ownerUserId") Long ownerUserId);

    @Update("""
            WITH target_project AS MATERIALIZED (
                SELECT project_id
                FROM novel_projects
                WHERE project_id = #{projectId}
                  AND deleted_at IS NOT NULL
                  AND (#{ownerUserId}::BIGINT IS NULL OR owner_user_id = #{ownerUserId})
                  AND (#{deletedBefore}::TIMESTAMPTZ IS NULL OR deleted_at < #{deletedBefore})
            ),
            target_story_bibles AS MATERIALIZED (
                SELECT story_bible_id FROM story_bibles WHERE project_id IN (SELECT project_id FROM target_project)
            ),
            target_changesets AS MATERIALIZED (
                SELECT changeset_id FROM story_bible_changesets
                WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)
            ),
            target_sessions AS MATERIALIZED (
                SELECT session_id FROM agent_sessions WHERE project_id IN (SELECT project_id FROM target_project)
            ),
            target_runs AS MATERIALIZED (
                SELECT run_id FROM agent_runs WHERE project_id IN (SELECT project_id FROM target_project)
            ),
            target_approvals AS MATERIALIZED (
                SELECT approval_request_id FROM agent_approval_requests
                WHERE project_id IN (SELECT project_id FROM target_project)
            ),
            target_jobs AS MATERIALIZED (
                SELECT job_id FROM ops_async_jobs WHERE project_id IN (SELECT project_id FROM target_project)
            ),
            target_object_keys AS MATERIALIZED (
                SELECT cover_original_object_key AS object_key FROM novel_projects WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT cover_display_object_key FROM novel_projects WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT cover_thumbnail_object_key FROM novel_projects WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT original_object_key FROM novel_cover_upload_sessions WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT display_object_key FROM novel_cover_upload_sessions WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT thumbnail_object_key FROM novel_cover_upload_sessions WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT object_key FROM rag_upload_sessions WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT origin_object_key FROM rag_documents WHERE project_id IN (SELECT project_id FROM target_project)
                UNION ALL SELECT snapshot_object_key FROM agent_context_epochs WHERE session_id IN (SELECT session_id FROM target_sessions)
                UNION ALL SELECT state_object_key FROM agent_checkpoints WHERE run_id IN (SELECT run_id FROM target_runs)
                UNION ALL SELECT object_key FROM agent_event_archives WHERE run_id IN (SELECT run_id FROM target_runs)
            ),
            d01 AS (DELETE FROM agent_approval_actions WHERE request_id IN (SELECT approval_request_id FROM target_approvals)),
            d02 AS (DELETE FROM agent_approval_requests WHERE approval_request_id IN (SELECT approval_request_id FROM target_approvals)),
            d03 AS (DELETE FROM story_bible_change_items WHERE changeset_id IN (SELECT changeset_id FROM target_changesets)),
            d04 AS (DELETE FROM story_bible_changesets WHERE changeset_id IN (SELECT changeset_id FROM target_changesets)),
            d05 AS (DELETE FROM story_bible_view_preferences WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d06 AS (DELETE FROM story_bible_progressions WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d07 AS (DELETE FROM story_bible_relations WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d08 AS (DELETE FROM story_bible_node_tags WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d09 AS (DELETE FROM story_bible_tags WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d10 AS (DELETE FROM story_bible_node_categories WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d11 AS (DELETE FROM story_bible_categories WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d12 AS (DELETE FROM story_bible_aliases WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d13 AS (DELETE FROM story_bible_nodes WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d14 AS (DELETE FROM story_bible_node_types WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d15 AS (DELETE FROM story_bibles WHERE story_bible_id IN (SELECT story_bible_id FROM target_story_bibles)),
            d16 AS (DELETE FROM agent_event_archives WHERE run_id IN (SELECT run_id FROM target_runs)),
            d17 AS (DELETE FROM agent_tool_call_executions WHERE run_id IN (SELECT run_id FROM target_runs)),
            d18 AS (DELETE FROM agent_tool_call_projections WHERE run_id IN (SELECT run_id FROM target_runs)),
            d19 AS (DELETE FROM agent_todo_projections WHERE run_id IN (SELECT run_id FROM target_runs)),
            d20 AS (DELETE FROM agent_run_projections WHERE run_id IN (SELECT run_id FROM target_runs)),
            d21 AS (DELETE FROM agent_artifacts WHERE run_id IN (SELECT run_id FROM target_runs)),
            d22 AS (DELETE FROM agent_checkpoints WHERE run_id IN (SELECT run_id FROM target_runs)),
            d23 AS (DELETE FROM agent_events WHERE run_id IN (SELECT run_id FROM target_runs)),
            d24 AS (DELETE FROM agent_run_model_bindings WHERE run_id IN (SELECT run_id FROM target_runs)),
            d25 AS (DELETE FROM agent_run_inputs WHERE run_id IN (SELECT run_id FROM target_runs)),
            d26 AS (DELETE FROM agent_run_pending_approvals WHERE project_id IN (SELECT project_id FROM target_project)),
            d27 AS (DELETE FROM agent_session_todos WHERE project_id IN (SELECT project_id FROM target_project)),
            d28 AS (DELETE FROM agent_messages WHERE session_id IN (SELECT session_id FROM target_sessions)),
            d29 AS (DELETE FROM agent_turns WHERE session_id IN (SELECT session_id FROM target_sessions)),
            d30 AS (DELETE FROM agent_session_working_set WHERE session_id IN (SELECT session_id FROM target_sessions)),
            d31 AS (DELETE FROM agent_session_style_bindings WHERE session_id IN (SELECT session_id FROM target_sessions)),
            d32 AS (DELETE FROM agent_context_epochs WHERE session_id IN (SELECT session_id FROM target_sessions)),
            d33 AS (DELETE FROM agent_runs WHERE run_id IN (SELECT run_id FROM target_runs)),
            d34 AS (DELETE FROM agent_sessions WHERE session_id IN (SELECT session_id FROM target_sessions)),
            d35 AS (DELETE FROM ops_async_job_attempts WHERE job_id IN (SELECT job_id FROM target_jobs)),
            d36 AS (DELETE FROM ops_async_jobs WHERE job_id IN (SELECT job_id FROM target_jobs)),
            d37 AS (DELETE FROM rag_vectors_f32 WHERE project_id IN (SELECT project_id FROM target_project)),
            d38 AS (DELETE FROM rag_vectors_f16 WHERE project_id IN (SELECT project_id FROM target_project)),
            d39 AS (DELETE FROM rag_chunks WHERE project_id IN (SELECT project_id FROM target_project)),
            d40 AS (DELETE FROM rag_index_sources WHERE project_id IN (SELECT project_id FROM target_project)),
            d41 AS (DELETE FROM rag_index_builds WHERE project_id IN (SELECT project_id FROM target_project)),
            d42 AS (DELETE FROM rag_retrieval_logs WHERE project_id IN (SELECT project_id FROM target_project)),
            d43 AS (DELETE FROM rag_documents WHERE project_id IN (SELECT project_id FROM target_project)),
            d44 AS (DELETE FROM rag_upload_sessions WHERE project_id IN (SELECT project_id FROM target_project)),
            d44_cover AS (DELETE FROM novel_cover_upload_sessions WHERE project_id IN (SELECT project_id FROM target_project)),
            d45 AS (DELETE FROM project_ai_configurations WHERE project_id IN (SELECT project_id FROM target_project)),
            d46 AS (DELETE FROM plugin_call_logs WHERE project_id IN (SELECT project_id FROM target_project)),
            d47 AS (DELETE FROM plugin_project_installs WHERE project_id IN (SELECT project_id FROM target_project)),
            d48 AS (DELETE FROM style_switch_logs WHERE project_id IN (SELECT project_id FROM target_project)),
            d49 AS (DELETE FROM style_profiles WHERE project_id IN (SELECT project_id FROM target_project)),
            d50 AS (DELETE FROM novel_chapter_ai_undo_operations WHERE project_id IN (SELECT project_id FROM target_project)),
            d51 AS (DELETE FROM novel_chapters WHERE project_id IN (SELECT project_id FROM target_project)),
            d52 AS (DELETE FROM novel_volumes WHERE project_id IN (SELECT project_id FROM target_project)),
            d53 AS (DELETE FROM storage_objects WHERE object_key IN (SELECT object_key FROM target_object_keys WHERE object_key IS NOT NULL))
            DELETE FROM novel_projects WHERE project_id IN (SELECT project_id FROM target_project)
            """)
    int purgeDeleted(@Param("projectId") Long projectId,
                     @Param("ownerUserId") Long ownerUserId,
                     @Param("deletedBefore") Instant deletedBefore);
}

