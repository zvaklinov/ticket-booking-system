package com.hari.bookingservice.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * The producer-side envelope. Field names and order match the consumer-side EventEnvelope
 * exactly, so both serialise and deserialise to the same JSON shape — the contract lives in
 * that JSON, not in either Java type.
 *
 * payload is Object here because the caller passes a concrete payload record to be serialised.
 * On the consuming side it is a JsonNode instead, because a consumer must read eventType before
 * it knows which payload type to bind to.
 */
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