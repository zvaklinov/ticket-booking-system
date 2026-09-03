package com.hari.paymentservice.payment.dto;

import com.hari.paymentservice.payment.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID holdId,
        BigDecimal amount,
        String currency,
        String status,
        String clientSecret,
        Instant createdAt
) {
    /**
     * clientSecret is what a frontend would pass to Stripe.js to confirm the payment. It is
     * scoped to this one PaymentIntent and is safe to return to the paying customer — unlike
     * the secret key, which never leaves the server.
     */
    public static PaymentResponse from(Payment payment, String clientSecret) {
        return new PaymentResponse(
                payment.getId(), payment.getHoldId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus().name(),
                clientSecret, payment.getCreatedAt());
    }

    public static PaymentResponse from(Payment payment) {
        return from(payment, null);
    }
}