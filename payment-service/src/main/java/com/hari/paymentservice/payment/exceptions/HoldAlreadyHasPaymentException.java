package com.hari.paymentservice.payment.exceptions;

import java.util.UUID;

public class HoldAlreadyHasPaymentException extends RuntimeException {

    private final UUID holdId;
    private final UUID paymentId;

    public HoldAlreadyHasPaymentException(UUID holdId, UUID paymentId) {
        super("Hold " + holdId + " already has a payment in progress");
        this.holdId = holdId;
        this.paymentId = paymentId;
    }

    public UUID getHoldId() { return holdId; }
    public UUID getPaymentId() { return paymentId; }
}