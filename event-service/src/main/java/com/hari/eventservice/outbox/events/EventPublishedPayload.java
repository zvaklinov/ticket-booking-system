package com.hari.eventservice.outbox.events;

import java.time.Instant;
import java.util.UUID;

public record EventPublishedPayload(
        UUID eventId,
        String title,
        UUID categoryId,
        String location,
        String venueTimezone,
        Instant startTimeUtc,
        Instant endTimeUtc,
        Instant bookingOpensAtUtc,
        Instant bookingClosesAtUtc,
        String currency,
        String status
) {}