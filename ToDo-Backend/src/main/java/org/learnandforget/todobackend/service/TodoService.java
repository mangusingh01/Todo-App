package org.learnandforget.todobackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learnandforget.todobackend.cache.TodoCacheService;
import org.learnandforget.todobackend.dto.TodoDto;
import org.learnandforget.todobackend.exception.TodoNotFoundException;
import org.learnandforget.todobackend.kafka.TodoEvent;
import org.learnandforget.todobackend.kafka.TodoEventProducer;
import org.learnandforget.todobackend.model.Todo;
import org.learnandforget.todobackend.model.User;
import org.learnandforget.todobackend.repository.TodoJpaRepository;
import org.learnandforget.todobackend.repository.TodoRepository;
import org.learnandforget.todobackend.metrics.TodoMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TodoService {

    private final TodoJpaRepository todoJpaRepository;
    private final TodoRepository todoRepository;         // JDBC for bulk reads
    private final TodoEventProducer eventProducer;
    private final TodoCacheService cacheService;
    private final TodoMetrics metrics;

    // ── READ operations (cache-aside pattern) ─────────────────────

    /**
     * Cache-aside pattern:
     * 1. Check Redis first
     * 2. On miss — query DB via JDBC, store in Redis, return
     * 3. On hit  — return from Redis directly
     *
     * TTL handles eventual expiry. Explicit invalidation on writes
     * ensures users never see stale data after their own mutations.
     */
    public List<TodoDto.Response> getTodosForUser(Long userId) {
        List<TodoDto.Response> cached = cacheService.getTodoList(userId);
        if (cached != null) {
            log.debug("Cache HIT for userId={}", userId);
            metrics.incrementCacheHit();
            return cached;
        }

        log.debug("Cache MISS for userId={} — querying DB", userId);
        metrics.incrementCacheMiss();
        List<TodoDto.Response> todos = todoRepository.findAllByUserId(userId);
        cacheService.putTodoList(userId, todos);
        return todos;
    }

    public List<TodoDto.Response> getTodosByStatus(Long userId, Todo.Status status) {
        // Status-filtered queries bypass cache — too many cache key combinations.
        // Hot path is always "all todos for user" which IS cached.
        return todoRepository.findByUserIdAndStatus(userId, status);
    }

    public List<TodoDto.Response> getOverdueTodos(Long userId) {
        return todoRepository.findOverdueByUserId(userId);
    }

    public TodoDto.SummaryResponse getSummary(Long userId) {
        TodoDto.SummaryResponse cached = cacheService.getSummary(userId);
        if (cached != null) return cached;

        TodoDto.SummaryResponse summary = todoRepository.getSummaryByUserId(userId);
        cacheService.putSummary(userId, summary);
        return summary;
    }

    // ── WRITE operations (invalidate cache + publish Kafka event) ─

    @Transactional
    public TodoDto.Response createTodo(TodoDto.CreateRequest request, User user) {
        Todo todo = Todo.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority() != null
                        ? request.getPriority() : Todo.Priority.MEDIUM)
                .dueDate(request.getDueDate())
                .build();

        Todo saved = todoJpaRepository.save(todo);

        // Invalidate cache — user's list is now stale
        cacheService.evictUserCache(user.getId());

        // Publish to Kafka — async, does not block HTTP response
        eventProducer.publish(TodoEvent.of(
                TodoEvent.EventType.CREATED,
                saved.getId(), user.getId(),
                saved.getTitle(), saved.getStatus(), saved.getPriority()
        ));

        log.info("Created todo [id={}, userId={}]", saved.getId(), user.getId());
        metrics.incrementTodoCreated();
        return TodoDto.Response.from(saved);
    }

    @Transactional
    public TodoDto.Response updateTodo(Long todoId, TodoDto.UpdateRequest request, User user) {
        // findByIdAndUserId enforces ownership in a single query
        Todo todo = todoJpaRepository.findByIdAndUserId(todoId, user.getId())
                .orElseThrow(() -> new TodoNotFoundException(todoId));

        if (request.getTitle()       != null) todo.setTitle(request.getTitle());
        if (request.getDescription() != null) todo.setDescription(request.getDescription());
        if (request.getStatus()      != null) todo.setStatus(request.getStatus());
        if (request.getPriority()    != null) todo.setPriority(request.getPriority());
        if (request.getDueDate()     != null) todo.setDueDate(request.getDueDate());

        Todo updated = todoJpaRepository.save(todo);

        cacheService.evictUserCache(user.getId());

        eventProducer.publish(TodoEvent.of(
                TodoEvent.EventType.UPDATED,
                updated.getId(), user.getId(),
                updated.getTitle(), updated.getStatus(), updated.getPriority()
        ));

        log.info("Updated todo [id={}, userId={}]", updated.getId(), user.getId());
        metrics.incrementTodoUpdated();
        return TodoDto.Response.from(updated);
    }

    @Transactional
    public void deleteTodo(Long todoId, User user) {
        Todo todo = todoJpaRepository.findByIdAndUserId(todoId, user.getId())
                .orElseThrow(() -> new TodoNotFoundException(todoId));

        todoJpaRepository.delete(todo);

        cacheService.evictUserCache(user.getId());

        eventProducer.publish(TodoEvent.of(
                TodoEvent.EventType.DELETED,
                todo.getId(), user.getId(),
                todo.getTitle(), todo.getStatus(), todo.getPriority()
        ));

        log.info("Deleted todo [id={}, userId={}]", todoId, user.getId());
        metrics.incrementTodoDeleted();
    }
}