package com.hari.paymentservice.common;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String key) {
        super("Idempotency key '" + key + "' was already used with a different request payload");
    }
}