package com.hari.paymentservice.payment;

import java.util.List;

public enum PaymentStatus {
    CREATED,
    REQUIRES_PAYMENT_METHOD,
    REQUIRES_ACTION,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELLED || this == REFUNDED;
    }

    public boolean isInFlight() {
        return this == CREATED || this == REQUIRES_PAYMENT_METHOD
                || this == REQUIRES_ACTION || this == PROCESSING;
    }

    public static final List<PaymentStatus> DEAD = List.of(FAILED, CANCELLED);
}