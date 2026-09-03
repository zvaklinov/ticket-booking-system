package com.hari.paymentservice.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutgoingEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        String causationId,
        String aggregateId,
        int payloadSchemaVersion,
        Object payload
) {}