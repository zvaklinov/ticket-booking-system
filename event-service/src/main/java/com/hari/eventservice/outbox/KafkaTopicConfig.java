package com.hari.eventservice.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String EVENT_LIFECYCLE_TOPIC = "event.lifecycle.v1";

    @Bean
    public NewTopic eventLifecycleTopic() {
        // One partition in development. Ordering is guaranteed only within a partition, so
        // with several partitions, events for one eventId still arrive in order (they share
        // a key, hence a partition) while different events may interleave — which is fine.
        return TopicBuilder.name(EVENT_LIFECYCLE_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}