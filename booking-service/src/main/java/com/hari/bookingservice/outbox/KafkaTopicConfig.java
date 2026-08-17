package com.hari.bookingservice.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String BOOKING_LIFECYCLE_TOPIC = "booking.lifecycle.v1";

    @Bean
    public NewTopic bookingLifecycleTopic() {
        return TopicBuilder.name(BOOKING_LIFECYCLE_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}