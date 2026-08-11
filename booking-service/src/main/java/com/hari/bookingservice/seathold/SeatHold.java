package com.hari.bookingservice.seathold;

import com.hari.bookingservice.seathold.exceptions.InvalidSeatHoldTransitionException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_hold")
public class SeatHold {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatHoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "payment_deadline_at")
    private Instant paymentDeadlineAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected SeatHold() {
    }

    public SeatHold(UUID eventId, UUID userId, Instant expiresAt,
                    BigDecimal totalAmount, String currency) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt is required");
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalAmount must be zero or greater");
        }
        if (!"EUR".equals(currency)) throw new IllegalArgumentException("currency must be EUR");

        this.eventId = eventId;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = SeatHoldStatus.ACTIVE;
    }

    public void release(Instant now) {
        if (this.status == SeatHoldStatus.RELEASED) {
            return;
        }
        if (this.status != SeatHoldStatus.ACTIVE) {
            throw new InvalidSeatHoldTransitionException(this.status, SeatHoldStatus.RELEASED);
        }
        this.status = SeatHoldStatus.RELEASED;
        this.releasedAt = now;
    }

    public void expire(Instant now) {
        if (this.status == SeatHoldStatus.EXPIRED) {
            return;
        }
        if (this.status != SeatHoldStatus.ACTIVE && this.status != SeatHoldStatus.PAYMENT_PENDING) {
            throw new InvalidSeatHoldTransitionException(this.status, SeatHoldStatus.EXPIRED);
        }
        this.status = SeatHoldStatus.EXPIRED;
        this.releasedAt = now;
    }

    public void reserveForPayment(Instant paymentDeadlineAt) {
        if (this.status == SeatHoldStatus.PAYMENT_PENDING) {
            return;
        }
        if (this.status != SeatHoldStatus.ACTIVE) {
            throw new InvalidSeatHoldTransitionException(this.status, SeatHoldStatus.PAYMENT_PENDING);
        }
        this.status = SeatHoldStatus.PAYMENT_PENDING;
        this.paymentDeadlineAt = paymentDeadlineAt;
    }

    public void confirm(Instant now) {
        if (this.status == SeatHoldStatus.CONFIRMED) {
            return;
        }
        if (this.status != SeatHoldStatus.PAYMENT_PENDING) {
            throw new InvalidSeatHoldTransitionException(this.status, SeatHoldStatus.CONFIRMED);
        }
        this.status = SeatHoldStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public SeatHoldStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaymentDeadlineAt() { return paymentDeadlineAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
