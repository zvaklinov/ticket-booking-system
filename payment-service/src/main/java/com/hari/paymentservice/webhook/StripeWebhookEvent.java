package com.hari.paymentservice.webhook;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "stripe_webhook_event")
public class StripeWebhookEvent {

    /** Stripe's own event id. Making it the primary key means duplicates cannot be inserted. */
    @Id
    @Column(name = "stripe_event_id")
    private String stripeEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected StripeWebhookEvent() {
    }

    public StripeWebhookEvent(String stripeEventId, String eventType,
                              String paymentIntentId, String payload) {
        this.stripeEventId = stripeEventId;
        this.eventType = eventType;
        this.paymentIntentId = paymentIntentId;
        this.payload = payload;
        this.processingStatus = "RECEIVED";
    }

    public void markProcessed(Instant now) {
        this.processingStatus = "PROCESSED";
        this.processedAt = now;
        this.errorMessage = null;
    }

    public void markFailed(String error) {
        this.processingStatus = "FAILED";
        this.attemptCount++;
        this.errorMessage = error != null && error.length() > 1000
                ? error.substring(0, 1000) : error;
    }

    public String getStripeEventId() { return stripeEventId; }
    public String getEventType() { return eventType; }
    public String getPaymentIntentId() { return paymentIntentId; }
    public String getProcessingStatus() { return processingStatus; }
}