package com.hari.eventservice.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BookingLifecycleConsumer {

    private final BookingLifecycleHandler handler;
    private final JsonMapper jsonMapper;

    public BookingLifecycleConsumer(BookingLifecycleHandler handler, JsonMapper jsonMapper) {
        this.handler = handler;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "booking.lifecycle.v1", groupId = "event-service")
    public void onMessage(String message) {
        handler.handle(jsonMapper.readValue(message, IncomingEnvelope.class));
    }
}