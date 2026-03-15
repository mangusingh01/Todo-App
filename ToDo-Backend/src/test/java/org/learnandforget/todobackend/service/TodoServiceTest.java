package org.learnandforget.todobackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnandforget.todobackend.cache.cache.TodoCacheService;
import org.learnandforget.todobackend.dto.TodoDto;
import org.learnandforget.todobackend.exception.TodoNotFoundException;
import org.learnandforget.todobackend.kafka.TodoEvent;
import org.learnandforget.todobackend.kafka.TodoEventProducer;
import org.learnandforget.todobackend.metrics.TodoMetrics;
import org.learnandforget.todobackend.model.Todo;
import org.learnandforget.todobackend.model.User;
import org.learnandforget.todobackend.repository.TodoJpaRepository;
import org.learnandforget.todobackend.repository.TodoRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodoService")
class TodoServiceTest {

    @Mock private TodoJpaRepository todoJpaRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private TodoEventProducer eventProducer;
    @Mock private TodoCacheService cacheService;
    @Mock private TodoMetrics metrics;

    @InjectMocks
    private TodoService todoService;

    private User testUser;
    private Todo testTodo;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("mangu")
                .email("mangu@test.com")
                .password("encoded_password")
                .build();

        testTodo = Todo.builder()
                .id(10L)
                .user(testUser)
                .title("Write unit tests")
                .status(Todo.Status.PENDING)
                .priority(Todo.Priority.HIGH)
                .build();
    }

    // ── GET todos ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getTodosForUser")
    class GetTodosForUser {

        @Test
        @DisplayName("returns cached list when cache hit")
        void returnsCachedList_whenCacheHit() {
            List<TodoDto.Response> cached = List.of(TodoDto.Response.from(testTodo));
            when(cacheService.getTodoList(1L)).thenReturn(cached);

            List<TodoDto.Response> result = todoService.getTodosForUser(1L);

            assertThat(result).isEqualTo(cached);
            verify(todoRepository, never()).findAllByUserId(any());
            verify(metrics).incrementCacheHit();
        }

        @Test
        @DisplayName("queries DB and populates cache on cache miss")
        void queriesDbAndPopulatesCache_whenCacheMiss() {
            List<TodoDto.Response> dbResult = List.of(TodoDto.Response.from(testTodo));
            when(cacheService.getTodoList(1L)).thenReturn(null);
            when(todoRepository.findAllByUserId(1L)).thenReturn(dbResult);

            List<TodoDto.Response> result = todoService.getTodosForUser(1L);

            assertThat(result).isEqualTo(dbResult);
            verify(todoRepository).findAllByUserId(1L);
            verify(cacheService).putTodoList(1L, dbResult);
            verify(metrics).incrementCacheMiss();
        }
    }

    // ── CREATE ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createTodo")
    class CreateTodo {

        @Test
        @DisplayName("saves todo, evicts cache, publishes Kafka event")
        void savesTodo_evictsCache_publishesEvent() {
            TodoDto.CreateRequest request = TodoDto.CreateRequest.builder()
                    .title("Write unit tests")
                    .priority(Todo.Priority.HIGH)
                    .build();

            when(todoJpaRepository.save(any(Todo.class))).thenReturn(testTodo);

            TodoDto.Response response = todoService.createTodo(request, testUser);

            assertThat(response.getTitle()).isEqualTo("Write unit tests");
            assertThat(response.getId()).isEqualTo(10L);

            verify(todoJpaRepository).save(any(Todo.class));
            verify(cacheService).evictUserCache(1L);
            verify(metrics).incrementTodoCreated();

            // Verify Kafka event has correct type and userId
            ArgumentCaptor<TodoEvent> eventCaptor = ArgumentCaptor.forClass(TodoEvent.class);
            verify(eventProducer).publish(eventCaptor.capture());
            TodoEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.getEventType()).isEqualTo(TodoEvent.EventType.CREATED);
            assertThat(publishedEvent.getUserId()).isEqualTo(1L);
            assertThat(publishedEvent.getTodoId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("uses MEDIUM priority when none specified")
        void usesMediumPriority_whenNotSpecified() {
            TodoDto.CreateRequest request = TodoDto.CreateRequest.builder()
                    .title("No priority set")
                    .build();

            ArgumentCaptor<Todo> todoCaptor = ArgumentCaptor.forClass(Todo.class);
            when(todoJpaRepository.save(todoCaptor.capture())).thenReturn(testTodo);

            todoService.createTodo(request, testUser);

            assertThat(todoCaptor.getValue().getPriority()).isEqualTo(Todo.Priority.MEDIUM);
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTodo")
    class UpdateTodo {

        @Test
        @DisplayName("updates only non-null fields (partial update)")
        void updatesOnlyNonNullFields() {
            TodoDto.UpdateRequest request = TodoDto.UpdateRequest.builder()
                    .status(Todo.Status.DONE)
                    .build();  // title and others are null — should not change

            when(todoJpaRepository.findByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(testTodo));
            when(todoJpaRepository.save(any())).thenReturn(testTodo);

            todoService.updateTodo(10L, request, testUser);

            // Status changed, title untouched
            assertThat(testTodo.getStatus()).isEqualTo(Todo.Status.DONE);
            assertThat(testTodo.getTitle()).isEqualTo("Write unit tests");
            verify(cacheService).evictUserCache(1L);
            verify(metrics).incrementTodoUpdated();
        }

        @Test
        @DisplayName("throws TodoNotFoundException when todo not owned by user")
        void throwsNotFound_whenTodoNotOwnedByUser() {
            when(todoJpaRepository.findByIdAndUserId(99L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    todoService.updateTodo(99L, new TodoDto.UpdateRequest(), testUser))
                    .isInstanceOf(TodoNotFoundException.class);

            verify(todoJpaRepository, never()).save(any());
            verify(cacheService, never()).evictUserCache(any());
            verify(eventProducer, never()).publish(any());
        }
    }

    // ── DELETE ────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTodo")
    class DeleteTodo {

        @Test
        @DisplayName("deletes todo, evicts cache, publishes DELETE event")
        void deletesTodo_evictsCache_publishesEvent() {
            when(todoJpaRepository.findByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(testTodo));

            todoService.deleteTodo(10L, testUser);

            verify(todoJpaRepository).delete(testTodo);
            verify(cacheService).evictUserCache(1L);
            verify(metrics).incrementTodoDeleted();

            ArgumentCaptor<TodoEvent> captor = ArgumentCaptor.forClass(TodoEvent.class);
            verify(eventProducer).publish(captor.capture());
            assertThat(captor.getValue().getEventType())
                    .isEqualTo(TodoEvent.EventType.DELETED);
        }

        @Test
        @DisplayName("throws TodoNotFoundException when todo not found")
        void throwsNotFound_whenTodoNotFound() {
            when(todoJpaRepository.findByIdAndUserId(99L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> todoService.deleteTodo(99L, testUser))
                    .isInstanceOf(TodoNotFoundException.class);

            verify(todoJpaRepository, never()).delete(any());
        }
    }
}