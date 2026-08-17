package com.hari.bookingservice.outbox.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingConfirmedPayload(
        UUID bookingId, UUID holdId, UUID eventId, UUID userId,
        List<UUID> seatIds, BigDecimal totalAmount, String currency, Instant confirmedAt) {}
