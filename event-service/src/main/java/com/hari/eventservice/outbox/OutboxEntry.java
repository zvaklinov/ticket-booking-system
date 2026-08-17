package com.hari.eventservice.outbox;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    // Stored as jsonb so the payload stays queryable for debugging. If Hibernate complains
    // about the type mapping, changing the column to TEXT and dropping this annotation works
    // identically for our purposes.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEntry() {
    }

    public OutboxEntry(UUID aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        // Truncated: a full stack trace in a database column is rarely worth the space,
        // and the real diagnosis happens in the logs.
        this.lastError = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttemptCount() { return attemptCount; }
}