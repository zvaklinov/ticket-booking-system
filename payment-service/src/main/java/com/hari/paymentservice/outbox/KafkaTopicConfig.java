package com.hari.paymentservice.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PAYMENT_LIFECYCLE_TOPIC = "payment.lifecycle.v1";

    @Bean
    public NewTopic paymentLifecycleTopic() {
        return TopicBuilder.name(PAYMENT_LIFECYCLE_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}