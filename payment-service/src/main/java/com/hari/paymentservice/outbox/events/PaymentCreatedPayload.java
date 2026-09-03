package com.hari.paymentservice.outbox.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreatedPayload(
        UUID paymentId, UUID holdId, UUID userId,
        BigDecimal amount, String currency, String status) {}