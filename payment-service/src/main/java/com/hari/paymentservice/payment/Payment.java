package com.hari.paymentservice.payment;

import com.hari.paymentservice.payment.exceptions.InvalidPaymentTransitionException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "hold_id", nullable = false, updatable = false)
    private UUID holdId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "refund_submitted_at")
    private Instant refundSubmittedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_attempt_count", nullable = false)
    private int refundAttemptCount;

    @Version
    private int version;

    protected Payment() {
    }

    public Payment(UUID holdId, UUID userId, BigDecimal amount, String currency) {
        if (holdId == null) throw new IllegalArgumentException("holdId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be zero or greater");
        }
        if (!"EUR".equals(currency)) throw new IllegalArgumentException("currency must be EUR");

        this.holdId = holdId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.CREATED;
    }

    public void attachPaymentIntent(String stripePaymentIntentId, PaymentStatus initialStatus) {
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.status = initialStatus;
    }

    public void applyWebhookStatus(PaymentStatus newStatus, Instant now) {
        if (this.status == newStatus) {
            return;
        }

        if (this.status.isTerminal() && newStatus != PaymentStatus.SUCCEEDED) {
            return;
        }

        this.status = newStatus;
        if (newStatus == PaymentStatus.SUCCEEDED) {
            this.completedAt = now;
        }
    }

    /** Timeout give-up. Distinct from CANCELLED, which is an explicit request. */
    public void markFailed(Instant now) {
        if (this.status == PaymentStatus.FAILED) {
            return;
        }
        if (!this.status.isInFlight()) {
            throw new InvalidPaymentTransitionException(this.status, PaymentStatus.FAILED);
        }
        this.status = PaymentStatus.FAILED;
        this.failedAt = now;
    }

    public void markCancelled(Instant now) {
        if (this.status == PaymentStatus.CANCELLED) {
            return;
        }
        if (this.status == PaymentStatus.SUCCEEDED) {
            throw new InvalidPaymentTransitionException(this.status, PaymentStatus.CANCELLED);
        }
        this.status = PaymentStatus.CANCELLED;
        this.failedAt = now;
    }

    public void markLateSuccess(Instant now) {
        this.status = PaymentStatus.SUCCEEDED;
        this.completedAt = now;
    }

    public void startRefund() {
        if (this.status == PaymentStatus.REFUND_PENDING) {
            return;
        }
        if (this.status != PaymentStatus.SUCCEEDED && this.status != PaymentStatus.REFUND_FAILED) {
            throw new InvalidPaymentTransitionException(this.status, PaymentStatus.REFUND_PENDING);
        }
        this.status = PaymentStatus.REFUND_PENDING;
        this.refundSubmittedAt = null;
        this.refundAttemptCount++;
    }

    public void markRefunded(Instant now) {
        if (this.status == PaymentStatus.REFUNDED) {
            return;
        }
        if (this.status != PaymentStatus.REFUND_PENDING) {
            throw new InvalidPaymentTransitionException(this.status, PaymentStatus.REFUNDED);
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = now;
    }

    public void markRefundSubmitted(Instant now) {
        this.refundSubmittedAt = now;
    }

    public void markRefundFailed() {
        if (this.status == PaymentStatus.REFUND_FAILED) {
            return;
        }
        if (this.status != PaymentStatus.REFUND_PENDING) {
            throw new InvalidPaymentTransitionException(this.status, PaymentStatus.REFUND_FAILED);
        }
        this.status = PaymentStatus.REFUND_FAILED;
        this.refundSubmittedAt = null;
    }

    public UUID getId() { return id; }
    public UUID getHoldId() { return holdId; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getRefundAttemptCount() { return refundAttemptCount; }
    public Instant getRefundSubmittedAt() { return refundSubmittedAt; }
}