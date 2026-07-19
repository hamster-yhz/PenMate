package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentContextEpoch;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentContextEpochMapper {

    @Select("""
            SELECT session_id FROM agent_sessions
            WHERE session_id = #{sessionId} AND deleted_at IS NULL
            LIMIT 1 FOR UPDATE
            """)
    Long lockSession(Long sessionId);

    @Select("""
            SELECT e.epoch_id, e.session_id, e.epoch_no, e.fingerprint, e.story_bible_revision,
                   e.manuscript_revision, e.active_chapter_id, e.active_chapter_content_revision,
                   e.style_binding_revision, e.routing_mode,
                   e.router_model_config_id, e.prompt_bundle_hash,
                   e.skill_catalog_hash, e.tool_catalog_hash, e.snapshot_object_key, e.snapshot_hash,
                   e.snapshot_size_bytes, e.created_at, e.superseded_at
            FROM agent_context_epochs e
            JOIN agent_sessions s ON s.session_id = e.session_id AND s.active_context_epoch_id = e.epoch_id
            WHERE e.session_id = #{sessionId} AND e.fingerprint = #{fingerprint}
            LIMIT 1
            """)
    AgentContextEpoch findCurrentByFingerprint(@Param("sessionId") Long sessionId,
                                               @Param("fingerprint") String fingerprint);

    @Select("""
            SELECT epoch_id, session_id, epoch_no, fingerprint, story_bible_revision, manuscript_revision,
                   active_chapter_id, active_chapter_content_revision, style_binding_revision, routing_mode, router_model_config_id,
                   prompt_bundle_hash, skill_catalog_hash, tool_catalog_hash,
                   snapshot_object_key, snapshot_hash, snapshot_size_bytes, created_at, superseded_at
            FROM agent_context_epochs WHERE epoch_id = #{epochId} LIMIT 1
            """)
    AgentContextEpoch findById(Long epochId);

    @Select("SELECT COALESCE(MAX(epoch_no), 0) + 1 FROM agent_context_epochs WHERE session_id = #{sessionId}")
    int nextEpochNo(Long sessionId);

    @Insert("""
            INSERT INTO agent_context_epochs(
                epoch_id, session_id, epoch_no, fingerprint, story_bible_revision, manuscript_revision,
                active_chapter_id, active_chapter_content_revision, style_binding_revision, routing_mode, router_model_config_id,
                prompt_bundle_hash, skill_catalog_hash, tool_catalog_hash,
                snapshot_object_key, snapshot_hash, snapshot_size_bytes
            ) VALUES (
                #{epochId}, #{sessionId}, #{epochNo}, #{fingerprint}, #{storyBibleRevision}, #{manuscriptRevision},
                #{activeChapterId}, #{activeChapterContentRevision}, #{styleBindingRevision}, #{routingMode}, #{routerModelConfigId},
                #{promptBundleHash}, #{skillCatalogHash}, #{toolCatalogHash},
                #{snapshotObjectKey}, #{snapshotHash}, #{snapshotSizeBytes}
            )
            """)
    int insert(AgentContextEpoch epoch);

    @Update("""
            UPDATE agent_context_epochs
            SET superseded_at = CURRENT_TIMESTAMP(3)
            WHERE session_id = #{sessionId}
              AND epoch_id = (
                  SELECT active_context_epoch_id FROM agent_sessions WHERE session_id = #{sessionId}
              )
              AND epoch_id != #{nextEpochId} AND superseded_at IS NULL
            """)
    int supersedeCurrent(@Param("sessionId") Long sessionId, @Param("nextEpochId") Long nextEpochId);

    @Update("""
            UPDATE agent_sessions SET active_context_epoch_id = #{epochId}
            WHERE session_id = #{sessionId} AND deleted_at IS NULL
            """)
    int bindSession(@Param("sessionId") Long sessionId, @Param("epochId") Long epochId);

    @Update("""
            UPDATE agent_runs SET context_epoch_id = #{epochId}
            WHERE run_id = #{runId} AND (context_epoch_id IS NULL OR context_epoch_id = #{epochId})
            """)
    int bindRun(@Param("runId") Long runId, @Param("epochId") Long epochId);
}
