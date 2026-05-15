package com.penmate.backend.infrastructure.persistence.todo;

import com.penmate.backend.domain.todo.model.SessionTodo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * SessionTodo MyBatis Mapper。
 */
@Mapper
public interface SessionTodoMapper {

    @Select("""
            SELECT id,
                   todo_id AS todoId,
                   project_id AS projectId,
                   session_id AS sessionId,
                   task_id AS taskId,
                   title,
                   description,
                   source_type AS sourceType,
                   todo_status AS todoStatus,
                   completed_at AS completedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM agent_session_todos
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
              AND deleted_at IS NULL
            ORDER BY created_at ASC, id ASC
            """)
    List<SessionTodo> findBySession(@Param("projectId") Long projectId,
                                    @Param("sessionId") Long sessionId);

    @Select("""
            SELECT id,
                   todo_id AS todoId,
                   project_id AS projectId,
                   session_id AS sessionId,
                   task_id AS taskId,
                   title,
                   description,
                   source_type AS sourceType,
                   todo_status AS todoStatus,
                   completed_at AS completedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM agent_session_todos
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
              AND todo_status = #{todoStatus}
              AND deleted_at IS NULL
            ORDER BY created_at ASC, id ASC
            """)
    List<SessionTodo> findBySessionAndStatus(@Param("projectId") Long projectId,
                                             @Param("sessionId") Long sessionId,
                                             @Param("todoStatus") String todoStatus);

    @Select("""
            SELECT id,
                   todo_id AS todoId,
                   project_id AS projectId,
                   session_id AS sessionId,
                   task_id AS taskId,
                   title,
                   description,
                   source_type AS sourceType,
                   todo_status AS todoStatus,
                   completed_at AS completedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted_at AS deletedAt
            FROM agent_session_todos
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
              AND todo_id = #{todoId}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    SessionTodo findByTodoId(@Param("projectId") Long projectId,
                             @Param("sessionId") Long sessionId,
                             @Param("todoId") Long todoId);

    @Insert("""
            INSERT INTO agent_session_todos(
                todo_id, project_id, session_id, task_id, title, description, source_type, todo_status, completed_at
            ) VALUES (
                #{todoId}, #{projectId}, #{sessionId}, #{taskId}, #{title}, #{description}, #{sourceType}, #{todoStatus}, #{completedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SessionTodo sessionTodo);

    @Update("""
            UPDATE agent_session_todos
            SET task_id = #{taskId},
                title = #{title},
                description = #{description},
                source_type = #{sourceType},
                todo_status = #{todoStatus},
                completed_at = #{completedAt}
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
              AND todo_id = #{todoId}
              AND deleted_at IS NULL
            """)
    int update(SessionTodo sessionTodo);

    @Update("""
            UPDATE agent_session_todos
            SET todo_status = 'DONE',
                completed_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
              AND todo_id = #{todoId}
              AND deleted_at IS NULL
            """)
    int markCompleted(@Param("projectId") Long projectId,
                      @Param("sessionId") Long sessionId,
                      @Param("todoId") Long todoId);

    @Update("""
            UPDATE agent_session_todos
            SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId}
              AND session_id = #{sessionId}
              AND todo_id = #{todoId}
              AND deleted_at IS NULL
            """)
    int softDelete(@Param("projectId") Long projectId,
                   @Param("sessionId") Long sessionId,
                   @Param("todoId") Long todoId);
}
