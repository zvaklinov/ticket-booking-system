package com.hari.bookingservice.outbox;

import java.util.UUID;

public record BookingConfirmationFailedPayload(
        UUID paymentId, UUID holdId, UUID eventId, UUID userId, String reason) {}