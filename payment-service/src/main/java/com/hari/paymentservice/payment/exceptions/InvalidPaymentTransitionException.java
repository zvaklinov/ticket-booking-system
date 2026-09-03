package com.hari.paymentservice.payment.exceptions;

import com.hari.paymentservice.payment.PaymentStatus;

public class InvalidPaymentTransitionException extends RuntimeException {
    public InvalidPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Invalid payment transition: " + from + " -> " + to);
    }
}