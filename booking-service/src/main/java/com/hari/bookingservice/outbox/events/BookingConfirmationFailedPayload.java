package com.hari.bookingservice.outbox.events;

import java.time.Instant;
import java.util.UUID;

public record BookingConfirmationFailedPayload(
        UUID paymentId, UUID holdId, UUID eventId, UUID userId, String reason) {}
