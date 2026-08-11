package com.hari.bookingservice.seat;

import com.hari.bookingservice.seat.exceptions.InvalidSeatTransitionException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "seat_label", nullable = false)
    private String seatLabel;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(name = "active_hold_id")
    private UUID activeHoldId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected Seat() {
        // required by JPA
    }

    public Seat(UUID eventId, String seatLabel, BigDecimal price, String currency) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (seatLabel == null || seatLabel.isBlank()) {
            throw new IllegalArgumentException("seatLabel is required");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("price must be zero or greater");
        }
        if (!"EUR".equals(currency)) {
            throw new IllegalArgumentException("currency must be EUR");
        }

        this.eventId = eventId;
        this.seatLabel = seatLabel;
        this.price = price;
        this.currency = currency;
        this.status = SeatStatus.AVAILABLE;
    }

    public void markUnavailable() {
        if (this.status == SeatStatus.UNAVAILABLE) {
            return;
        }
        if (this.status != SeatStatus.AVAILABLE) {
            throw new InvalidSeatTransitionException(this.status, SeatStatus.UNAVAILABLE);
        }
        this.status = SeatStatus.UNAVAILABLE;
    }

    public void markAvailable() {
        if (this.status == SeatStatus.AVAILABLE) {
            return;
        }
        if (this.status != SeatStatus.UNAVAILABLE) {
            throw new InvalidSeatTransitionException(this.status, SeatStatus.AVAILABLE);
        }
        this.status = SeatStatus.AVAILABLE;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getSeatLabel() { return seatLabel; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public SeatStatus getStatus() { return status; }
    public UUID getActiveHoldId() { return activeHoldId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
