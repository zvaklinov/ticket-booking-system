package com.hari.bookingservice.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately a separate declaration from event-service's, not a shared library class.
 * Sharing it would couple the two services' build and release cycles, which is exactly what
 * the architecture is trying to avoid — the contract is the JSON on the wire, not a Java type.
 *
 * The payload stays as a JsonNode so this record can be parsed before we know which payload
 * type it holds.
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
        JsonNode payload
) {}