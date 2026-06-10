package com.penmate.backend.infrastructure.persistence.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.todo.model.SessionTodo;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionTodoRedisRepositoryTest {

    @Test
    void should_store_one_json_document_per_session_and_refresh_thirty_minute_ttl_on_save_and_load() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SessionTodoRepositoryImpl repository = new SessionTodoRepositoryImpl(redisTemplate, new ObjectMapper());
        SessionTodo todo = new SessionTodo();
        todo.setTodoId(101L);
        todo.setProjectId(10L);
        todo.setSessionId(20L);
        todo.setTitle("Read auth flow");
        todo.setStatus("pending");

        repository.saveSessionTodos(10L, 20L, List.of(todo));
        verify(valueOperations).set(eq("agent:session:10:20:todo"), org.mockito.ArgumentMatchers.contains("Read auth flow"), eq(Duration.ofMinutes(30)));

        when(valueOperations.get("agent:session:10:20:todo")).thenReturn("""
                {"projectId":10,"sessionId":20,"items":[{"todoId":101,"projectId":10,"sessionId":20,"title":"Read auth flow","status":"pending"}]}
                """);

        List<SessionTodo> loaded = repository.findBySession(10L, 20L, null);

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getTitle()).isEqualTo("Read auth flow");
        assertThat(loaded.get(0).getStatus()).isEqualTo("pending");
        verify(redisTemplate).expire("agent:session:10:20:todo", Duration.ofMinutes(30));
    }
}
