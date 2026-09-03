package com.hari.paymentservice.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SeatHoldPaymentDetails(
        UUID holdId, UUID eventId, UUID userId, String status,
        BigDecimal totalAmount, String currency, Instant expiresAt) {}