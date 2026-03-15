package org.learnandforget.todobackend.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom application metrics exposed via /actuator/prometheus.
 *
 * These go beyond the default Spring Boot metrics (JVM, HTTP latency)
 * and track business-level signals that matter for operating the app:
 *
 *  - todo.created / todo.updated / todo.deleted  — operation throughput
 *  - cache.hit / cache.miss                      — cache effectiveness
 *  - kafka.publish.success / kafka.publish.fail  — event pipeline health
 *
 * In Prometheus you can then alert on:
 *  - cache hit ratio dropping below 80%
 *  - kafka publish failures spiking
 *  - todo creation rate anomalies
 */
@Component
@Slf4j
public class TodoMetrics {

    // ── Todo operation counters ───────────────────────────────────
    private final Counter todoCreatedCounter;
    private final Counter todoUpdatedCounter;
    private final Counter todoDeletedCounter;

    // ── Cache counters ────────────────────────────────────────────
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    // ── Kafka counters ────────────────────────────────────────────
    private final Counter kafkaPublishSuccessCounter;
    private final Counter kafkaPublishFailCounter;

    public TodoMetrics(MeterRegistry registry) {
        todoCreatedCounter = Counter.builder("todo.created")
                .description("Number of todos created")
                .register(registry);

        todoUpdatedCounter = Counter.builder("todo.updated")
                .description("Number of todos updated")
                .register(registry);

        todoDeletedCounter = Counter.builder("todo.deleted")
                .description("Number of todos deleted")
                .register(registry);

        cacheHitCounter = Counter.builder("cache.hit")
                .tag("cache", "todo-list")
                .description("Redis cache hits for todo list")
                .register(registry);

        cacheMissCounter = Counter.builder("cache.miss")
                .tag("cache", "todo-list")
                .description("Redis cache misses for todo list")
                .register(registry);

        kafkaPublishSuccessCounter = Counter.builder("kafka.publish.success")
                .tag("topic", "todo-events")
                .description("Successful Kafka event publishes")
                .register(registry);

        kafkaPublishFailCounter = Counter.builder("kafka.publish.fail")
                .tag("topic", "todo-events")
                .description("Failed Kafka event publishes")
                .register(registry);
    }

    public void incrementTodoCreated()       { todoCreatedCounter.increment(); }
    public void incrementTodoUpdated()       { todoUpdatedCounter.increment(); }
    public void incrementTodoDeleted()       { todoDeletedCounter.increment(); }
    public void incrementCacheHit()          { cacheHitCounter.increment(); }
    public void incrementCacheMiss()         { cacheMissCounter.increment(); }
    public void incrementKafkaSuccess()      { kafkaPublishSuccessCounter.increment(); }
    public void incrementKafkaFail()         { kafkaPublishFailCounter.increment(); }

    /**
     * Cache hit ratio — useful to log periodically or expose via a Gauge.
     * hit / (hit + miss). Returns 0 if no requests yet.
     */
    public double getCacheHitRatio() {
        double hits   = cacheHitCounter.count();
        double misses = cacheMissCounter.count();
        double total  = hits + misses;
        return total == 0 ? 0 : hits / total;
    }
}