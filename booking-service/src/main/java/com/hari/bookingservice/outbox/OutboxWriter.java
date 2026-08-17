package com.hari.bookingservice.outbox;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

@Component
public class OutboxWriter {

    private static final String PRODUCER = "booking-service";
    private static final int EVENT_VERSION = 1;
    private static final int PAYLOAD_SCHEMA_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public OutboxWriter(OutboxRepository outboxRepository, JsonMapper jsonMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void write(String eventType, UUID aggregateId, Object payload) {
        OutgoingEnvelope  envelope = new OutgoingEnvelope (
                UUID.randomUUID(),
                eventType,
                EVENT_VERSION,
                clock.instant(),
                PRODUCER,
                UUID.randomUUID().toString(),
                null,
                aggregateId.toString(),
                PAYLOAD_SCHEMA_VERSION,
                payload);

        try {
            outboxRepository.save(new OutboxEntry(
                    aggregateId, eventType, jsonMapper.writeValueAsString(envelope)));
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize outbox payload for " + eventType, e);
        }
    }
}