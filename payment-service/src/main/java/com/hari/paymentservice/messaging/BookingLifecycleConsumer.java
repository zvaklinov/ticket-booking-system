package com.hari.paymentservice.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class BookingLifecycleConsumer {

    private final JsonMapper jsonMapper;
    private final BookingLifecycleHandler handler;

    BookingLifecycleConsumer(JsonMapper jsonMapper, BookingLifecycleHandler handler) {
        this.jsonMapper = jsonMapper;
        this.handler = handler;
    }

    @KafkaListener(topics = "booking.lifecycle.v1", groupId = "payment-service")
    void onMessage(String rawEnvelope) {
        handler.handle(jsonMapper.readValue(rawEnvelope, IncomingEnvelope.class));
    }
}