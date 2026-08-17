package com.hari.eventservice.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * The wire contract. Deliberately separate from any JPA entity — serializing entities directly
 * would couple every consumer to this service's internal schema, so a harmless local refactor
 * would become a breaking change for everyone downstream.
 */
public record EventEnvelope(
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