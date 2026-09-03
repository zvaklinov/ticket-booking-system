package com.hari.bookingservice.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only view for Payment Service. Carries no side effects — reserving is a separate POST. */
public record SeatHoldPaymentDetails(
        UUID holdId,
        UUID eventId,
        UUID userId,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant expiresAt
) {}