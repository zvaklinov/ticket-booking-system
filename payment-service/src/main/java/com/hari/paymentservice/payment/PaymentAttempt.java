package com.hari.paymentservice.payment;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Audit history. One row per Stripe confirmation attempt, including declines. */
@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(nullable = false)
    private String status;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID paymentId, int attemptNumber, String stripePaymentIntentId,
                          String status, String failureCode, String failureMessage,
                          Instant completedAt) {
        this.paymentId = paymentId;
        this.attemptNumber = attemptNumber;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.status = status;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
}