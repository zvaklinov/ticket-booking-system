package com.hari.paymentservice.payment.exceptions;

import java.util.UUID;

public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(UUID paymentId, Throwable cause) {
        super("The payment provider rejected or failed the request for payment " + paymentId, cause);
    }
}