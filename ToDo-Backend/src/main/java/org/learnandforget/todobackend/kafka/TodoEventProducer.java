package org.learnandforget.todobackend.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learnandforget.todobackend.metrics.TodoMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class TodoEventProducer {

    private final KafkaTemplate<String, TodoEvent> kafkaTemplate;
    private final TodoMetrics metrics;

    @Value("${app.kafka.topics.todo-events}")
    private String todoEventsTopic;

    /**
     * Publishes a TodoEvent asynchronously.
     *
     * Key = userId as String — ensures all events for the same user
     * land on the same partition, preserving per-user ordering.
     *
     * Fire-and-forget with callback logging — we do NOT block the
     * HTTP response on Kafka acknowledgment. If Kafka is slow or down,
     * the API still responds to the user.
     */
    public void publish(TodoEvent event) {
        String key = String.valueOf(event.getUserId());

        CompletableFuture<SendResult<String, TodoEvent>> future =
                kafkaTemplate.send(todoEventsTopic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                metrics.incrementKafkaFail();
                log.error("Failed to publish TodoEvent [type={}, todoId={}]: {}",
                        event.getEventType(), event.getTodoId(), ex.getMessage());
            } else {
                metrics.incrementKafkaSuccess();
                log.debug("Published TodoEvent [type={}, todoId={}, partition={}, offset={}]",
                        event.getEventType(),
                        event.getTodoId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}