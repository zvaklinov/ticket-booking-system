package com.hari.paymentservice.payment.exceptions;

import java.util.UUID;

public class PaymentAccessDeniedException extends RuntimeException {
    public PaymentAccessDeniedException(UUID paymentId) {
        super("Not the owner of payment: " + paymentId);
    }
}