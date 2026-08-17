package com.hari.bookingservice.messaging.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A deliberate subset of what event-service publishes. Ignoring unknown fields is what makes
 * the producer able to add fields without breaking this consumer.
 */
public record EventPublishedPayload(
        UUID eventId,
        String status,
        Instant bookingOpensAtUtc,
        Instant bookingClosesAtUtc,
        Instant startTimeUtc
) {}