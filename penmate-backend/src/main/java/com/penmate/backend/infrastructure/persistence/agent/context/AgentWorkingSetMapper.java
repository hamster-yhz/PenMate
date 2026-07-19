package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface AgentWorkingSetMapper {

    @Select("""
            SELECT session_id, node_id, activation_score, last_used_turn_id, use_count, pinned, updated_at
            FROM agent_session_working_set WHERE session_id = #{sessionId}
            ORDER BY pinned DESC, activation_score DESC, updated_at DESC, node_id
            """)
    List<AgentWorkingSetEntry> findBySessionId(Long sessionId);

    @Insert("""
            INSERT INTO agent_session_working_set(session_id, node_id, activation_score, last_used_turn_id, use_count, pinned)
            VALUES(#{sessionId}, #{nodeId}, #{score}, #{turnId}, 1, FALSE)
            ON CONFLICT (session_id, node_id) DO UPDATE SET
              activation_score = CASE
                  WHEN agent_session_working_set.last_used_turn_id = EXCLUDED.last_used_turn_id
                  THEN agent_session_working_set.activation_score
                  ELSE agent_session_working_set.activation_score + EXCLUDED.activation_score
              END,
              use_count = CASE
                  WHEN agent_session_working_set.last_used_turn_id = EXCLUDED.last_used_turn_id
                  THEN agent_session_working_set.use_count
                  ELSE agent_session_working_set.use_count + 1
              END,
              last_used_turn_id = EXCLUDED.last_used_turn_id,
              updated_at = CURRENT_TIMESTAMP(3)
            """)
    int promote(@Param("sessionId") Long sessionId, @Param("nodeId") Long nodeId,
                @Param("score") BigDecimal score, @Param("turnId") Long turnId);

    @Update("""
            UPDATE agent_session_working_set SET pinned = #{pinned}
            WHERE session_id = #{sessionId} AND node_id = #{nodeId}
            """)
    int setPinned(@Param("sessionId") Long sessionId, @Param("nodeId") Long nodeId,
                  @Param("pinned") boolean pinned);

    @Delete("""
            DELETE FROM agent_session_working_set ws
            USING agent_turns used_turn, agent_turns current_turn
            WHERE ws.session_id = #{sessionId} AND ws.pinned = FALSE
              AND used_turn.turn_id = ws.last_used_turn_id
              AND current_turn.turn_id = #{currentTurnId}
              AND current_turn.session_id = ws.session_id
              AND current_turn.turn_seq - used_turn.turn_seq >= #{retentionTurns}
            """)
    int evictExpired(@Param("sessionId") Long sessionId, @Param("currentTurnId") Long currentTurnId,
                     @Param("retentionTurns") int retentionTurns);

    @Delete("""
            <script>
            DELETE FROM agent_session_working_set WHERE id IN (
              SELECT id FROM (
                SELECT id FROM agent_session_working_set
                WHERE session_id = #{sessionId} AND pinned = FALSE
                ORDER BY activation_score DESC, updated_at DESC, id DESC
                OFFSET #{automaticCap}
              ) overflow_rows
            )
            </script>
            """)
    int evictOverflow(@Param("sessionId") Long sessionId, @Param("automaticCap") int automaticCap);
}
