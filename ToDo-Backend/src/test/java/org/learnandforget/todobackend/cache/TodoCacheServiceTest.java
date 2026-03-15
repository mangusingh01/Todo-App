package org.learnandforget.todobackend.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnandforget.todobackend.dto.TodoDto;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodoCacheService")
class TodoCacheServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private TodoCacheService cacheService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "todoListKeyPrefix", "todos:user:");
        ReflectionTestUtils.setField(cacheService, "todoItemKeyPrefix", "todo:item:");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("getTodoList returns null on cache miss")
    void getTodoList_returnsNull_onCacheMiss() {
        when(valueOps.get("todos:user:1")).thenReturn(null);

        List<TodoDto.Response> result = cacheService.getTodoList(1L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("putTodoList stores list in Redis")
    void putTodoList_storesInRedis() {
        List<TodoDto.Response> todos = List.of(
                TodoDto.Response.builder().id(1L).title("Test").build()
        );

        cacheService.putTodoList(1L, todos);

        verify(valueOps).set(eq("todos:user:1"), eq(todos), any());
    }

    @Test
    @DisplayName("evictUserCache deletes both list and summary keys")
    void evictUserCache_deletesBothKeys() {
        cacheService.evictUserCache(1L);

        verify(redisTemplate).delete("todos:user:1");
        verify(redisTemplate).delete("summary:user:1");
    }

    @Test
    @DisplayName("getTodoList returns null gracefully when Redis throws")
    void getTodoList_returnsNull_whenRedisFails() {
        when(valueOps.get(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // Must not throw — Redis failure must never crash the API
        List<TodoDto.Response> result = cacheService.getTodoList(1L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("putTodoList fails silently when Redis throws")
    void putTodoList_failsSilently_whenRedisFails() {
        doThrow(new RuntimeException("Redis connection refused"))
                .when(valueOps).set(anyString(), any(), any());

        // Must not throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> cacheService.putTodoList(1L, List.of())
        );
    }
}