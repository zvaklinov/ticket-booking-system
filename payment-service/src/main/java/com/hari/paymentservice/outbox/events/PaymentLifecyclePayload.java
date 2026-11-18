package com.hari.paymentservice.outbox.events;

import java.math.BigDecimal;
import java.util.UUID;

/** Shared shape for PaymentSucceeded, PaymentCancelled, PaymentFailed, RefundRequested. */
public record PaymentLifecyclePayload(
        UUID paymentId, UUID holdId, UUID userId,
        BigDecimal amount, String currency) {}