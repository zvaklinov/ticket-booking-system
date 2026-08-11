package com.hari.bookingservice.booking;

import com.hari.bookingservice.booking.exceptions.CancellationDeadlinePassedException;
import com.hari.bookingservice.booking.exceptions.InvalidBookingTransitionException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "source_hold_id", nullable = false, updatable = false)
    private UUID sourceHoldId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "cancellation_requested_at")
    private Instant cancellationRequestedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected Booking() {
    }

    public Booking(UUID sourceHoldId, UUID eventId, UUID userId,
                   BigDecimal totalAmount, String currency, Instant confirmedAt) {
        this.sourceHoldId = sourceHoldId;
        this.eventId = eventId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.confirmedAt = confirmedAt;
        this.status = BookingStatus.CONFIRMED;
    }

    public void requestCancellation(Instant now, Instant eventStartTimeUtc) {
        if (this.status == BookingStatus.CANCELLATION_PENDING || this.status == BookingStatus.CANCELLED) {
            return;
        }

        if (!now.isBefore(eventStartTimeUtc.minusSeconds(24 * 60 * 60))) {
            throw new CancellationDeadlinePassedException(this.id);
        }
        this.status = BookingStatus.CANCELLATION_PENDING;
        this.cancellationRequestedAt = now;
    }

    public void markCancelled(Instant now) {
        if (this.status == BookingStatus.CANCELLED) {
            return;
        }
        if (this.status != BookingStatus.CANCELLATION_PENDING) {
            throw new InvalidBookingTransitionException(this.status, BookingStatus.CANCELLED);
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public UUID getId() { return id; }
    public UUID getSourceHoldId() { return sourceHoldId; }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCancellationRequestedAt() { return cancellationRequestedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public int getVersion() { return version; }
}
