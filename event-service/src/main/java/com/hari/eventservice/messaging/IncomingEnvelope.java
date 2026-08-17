package com.hari.eventservice.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record IncomingEnvelope(
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