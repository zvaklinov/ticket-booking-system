package com.hari.paymentservice.webhook;

public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException(Throwable cause) {
        super("Stripe webhook signature verification failed", cause);
    }
}