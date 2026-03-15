package org.learnandforget.todobackend.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learnandforget.todobackend.dto.TodoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Centralises all Redis operations.
 *
 * Using manual RedisTemplate (not @Cacheable annotations) for:
 * - Fine-grained control over key naming and TTLs
 * - Explicit eviction logic on writes
 * - Easier to reason about in interviews ("I control exactly when cache is invalidated")
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TodoCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.todo-list-key-prefix}")
    private String todoListKeyPrefix;       // "todos:user:"

    @Value("${app.cache.todo-item-key-prefix}")
    private String todoItemKeyPrefix;       // "todo:item:"

    private static final Duration TODO_LIST_TTL    = Duration.ofMinutes(5);
    private static final Duration TODO_SUMMARY_TTL = Duration.ofMinutes(2);

    // ── Todo list ─────────────────────────────────────────────────

    public List<TodoDto.Response> getTodoList(Long userId) {
        String key = todoListKeyPrefix + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) return null;
            return objectMapper.convertValue(cached,
                    new TypeReference<List<TodoDto.Response>>() {});
        } catch (Exception e) {
            log.warn("Redis read failed for key={}: {}", key, e.getMessage());
            return null;   // Cache failure must never break the API
        }
    }

    public void putTodoList(Long userId, List<TodoDto.Response> todos) {
        String key = todoListKeyPrefix + userId;
        try {
            redisTemplate.opsForValue().set(key, todos, TODO_LIST_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for key={}: {}", key, e.getMessage());
        }
    }

    // ── Summary ───────────────────────────────────────────────────

    public TodoDto.SummaryResponse getSummary(Long userId) {
        String key = "summary:user:" + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) return null;
            return objectMapper.convertValue(cached, TodoDto.SummaryResponse.class);
        } catch (Exception e) {
            log.warn("Redis read failed for summary key userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    public void putSummary(Long userId, TodoDto.SummaryResponse summary) {
        String key = "summary:user:" + userId;
        try {
            redisTemplate.opsForValue().set(key, summary, TODO_SUMMARY_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for summary userId={}: {}", userId, e.getMessage());
        }
    }

    // ── Eviction ──────────────────────────────────────────────────

    /**
     * Evicts all cache entries for a user on any write operation.
     * Called by TodoService after every create/update/delete.
     */
    public void evictUserCache(Long userId) {
        try {
            redisTemplate.delete(todoListKeyPrefix + userId);
            redisTemplate.delete("summary:user:" + userId);
            log.debug("Evicted cache for userId={}", userId);
        } catch (Exception e) {
            log.warn("Cache eviction failed for userId={}: {}", userId, e.getMessage());
        }
    }
}