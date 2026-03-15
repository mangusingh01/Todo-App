package org.learnandforget.todobackend.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes todo-events and writes to the audit_log table.
 *
 * Deliberately separated from the producer/service layer —
 * side effects (audit, future notifications, analytics) should
 * never be in the critical HTTP path.
 *
 * Idempotency note: Kafka delivers at-least-once. If this consumer
 * crashes mid-write, the event will be redelivered. The audit_log
 * table accepts duplicate entries (append-only) so this is safe.
 * For exactly-once, you'd use Kafka transactions + a dedup key.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodoEventConsumer {

    private final JdbcTemplate jdbcTemplate;

    @KafkaListener(
            topics = "${app.kafka.topics.todo-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "3"   // 3 consumer threads = matches partition count
    )
    public void consume(@Payload TodoEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consuming TodoEvent [type={}, todoId={}, partition={}, offset={}]",
                event.getEventType(), event.getTodoId(), partition, offset);

        try {
            writeAuditLog(event);
        } catch (Exception e) {
            // Log but don't rethrow — rethrowing causes infinite retry loop.
            // In production: send to a Dead Letter Topic (DLT) instead.
            log.error("Failed to write audit log for event [todoId={}, type={}]: {}",
                    event.getTodoId(), event.getEventType(), e.getMessage());
        }
    }

    private void writeAuditLog(TodoEvent event) {
        String sql = """
                INSERT INTO audit_log (todo_id, user_id, action, payload, created_at)
                VALUES (?, ?, ?, JSON_OBJECT(
                    'title',    ?,
                    'status',   ?,
                    'priority', ?
                ), NOW())
                """;

        jdbcTemplate.update(sql,
                event.getTodoId(),
                event.getUserId(),
                event.getEventType().name(),
                event.getTitle(),
                event.getStatus() != null ? event.getStatus().name() : null,
                event.getPriority() != null ? event.getPriority().name() : null
        );
    }
}