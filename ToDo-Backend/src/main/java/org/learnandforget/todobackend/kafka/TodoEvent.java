package org.learnandforget.todobackend.kafka;

import lombok.*;
import org.learnandforget.todobackend.model.Todo;

import java.time.LocalDateTime;

/**
 * Event published to Kafka on every CUD (Create/Update/Delete) operation.
 *
 * Kept flat (no nested objects) intentionally — Kafka consumers
 * should not depend on your JPA entity structure. If the entity
 * changes, the event schema stays stable.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TodoEvent {

    public enum EventType {
        CREATED, UPDATED, DELETED
    }

    private EventType eventType;
    private Long todoId;
    private Long userId;
    private String title;
    private Todo.Status status;
    private Todo.Priority priority;
    private LocalDateTime occurredAt;

    public static TodoEvent of(EventType type, Long todoId, Long userId,
                               String title, Todo.Status status, Todo.Priority priority) {
        return TodoEvent.builder()
                .eventType(type)
                .todoId(todoId)
                .userId(userId)
                .title(title)
                .status(status)
                .priority(priority)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}