package org.learnandforget.todobackend.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.todo-events}")
    private String todoEventsTopic;

    /**
     * Declares the topic programmatically.
     * Spring Kafka will auto-create it on startup if it doesn't exist.
     * In production you'd provision topics via Terraform/Helm — not here.
     */
    @Bean
    public NewTopic todoEventsTopic() {
        return TopicBuilder.name(todoEventsTopic)
                .partitions(3)      // 3 partitions — allows 3 parallel consumers
                .replicas(1)        // 1 replica — dev only; production needs >= 2
                .build();
    }
}