package com.hari.bookingservice.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentLifecycleConsumer {

    private final PaymentLifecycleHandler handler;
    private final JsonMapper jsonMapper;

    public PaymentLifecycleConsumer(PaymentLifecycleHandler handler, JsonMapper jsonMapper) {
        this.handler = handler;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "payment.lifecycle.v1", groupId = "booking-service")
    public void onMessage(String message) {
        EventEnvelope envelope = jsonMapper.readValue(message, EventEnvelope.class);
        handler.handle(envelope);
    }
}