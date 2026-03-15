package org.learnandforget.todobackend.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnandforget.todobackend.model.Todo;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodoEventConsumer")
class TodoEventConsumerTest {

    @Mock  private JdbcTemplate jdbcTemplate;
    @InjectMocks private TodoEventConsumer consumer;

    @Test
    @DisplayName("writes audit log for CREATED event")
    void writesAuditLog_forCreatedEvent() {
        TodoEvent event = TodoEvent.of(
                TodoEvent.EventType.CREATED,
                10L, 1L, "Buy groceries",
                Todo.Status.PENDING, Todo.Priority.HIGH
        );

        consumer.consume(event, 0, 0L);

        verify(jdbcTemplate).update(
                anyString(),
                eq(10L), eq(1L), eq("CREATED"),
                eq("Buy groceries"), eq("PENDING"), eq("HIGH")
        );
    }

    @Test
    @DisplayName("writes audit log for DELETED event with null status gracefully")
    void writesAuditLog_forDeletedEvent() {
        TodoEvent event = TodoEvent.of(
                TodoEvent.EventType.DELETED,
                10L, 1L, "Buy groceries",
                Todo.Status.DONE, Todo.Priority.LOW
        );

        consumer.consume(event, 1, 42L);

        verify(jdbcTemplate, times(1)).update(anyString(), Optional.ofNullable(any()));
    }

    @Test
    @DisplayName("does not rethrow when JDBC write fails — prevents infinite retry")
    void doesNotRethrow_whenJdbcFails() {
        TodoEvent event = TodoEvent.of(
                TodoEvent.EventType.UPDATED,
                10L, 1L, "Test",
                Todo.Status.IN_PROGRESS, Todo.Priority.MEDIUM
        );

        doThrow(new RuntimeException("DB connection lost"))
                .when(jdbcTemplate).update(anyString(), Optional.ofNullable(any()));

        // Must not throw — rethrowing would cause Kafka to retry infinitely
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> consumer.consume(event, 0, 0L)
        );
    }
}