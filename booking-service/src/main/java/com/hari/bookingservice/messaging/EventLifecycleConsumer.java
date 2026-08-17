package com.hari.bookingservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EventLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventLifecycleConsumer.class);

    private final EventLifecycleHandler handler;
    private final JsonMapper jsonMapper;

    public EventLifecycleConsumer(EventLifecycleHandler handler, JsonMapper jsonMapper) {
        this.handler = handler;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "event.lifecycle.v1", groupId = "booking-service")
    public void onMessage(String message) {
        EventEnvelope envelope = jsonMapper.readValue(message, EventEnvelope.class);
        handler.handle(envelope);
    }
}