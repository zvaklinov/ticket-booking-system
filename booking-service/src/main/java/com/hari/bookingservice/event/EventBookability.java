package com.hari.bookingservice.event;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_bookability")
public class EventBookability {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String status;

    @Column(name = "booking_opens_at_utc", nullable = false)
    private Instant bookingOpensAtUtc;

    @Column(name = "booking_closes_at_utc", nullable = false)
    private Instant bookingClosesAtUtc;

    @Column(name = "start_time_utc", nullable = false)
    private Instant startTimeUtc;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventBookability() {
    }

    public EventBookability(UUID eventId, String status, Instant bookingOpensAtUtc,
                            Instant bookingClosesAtUtc, Instant startTimeUtc) {
        this.eventId = eventId;
        this.status = status;
        this.bookingOpensAtUtc = bookingOpensAtUtc;
        this.bookingClosesAtUtc = bookingClosesAtUtc;
        this.startTimeUtc = startTimeUtc;
    }

    public boolean isBookableAt(Instant now) {
        return "PUBLISHED".equals(status)
                && !now.isBefore(bookingOpensAtUtc)
                && now.isBefore(bookingClosesAtUtc);
    }

    public void apply(String status, Instant bookingOpensAtUtc,
                      Instant bookingClosesAtUtc, Instant startTimeUtc) {
        this.status = status;
        this.bookingOpensAtUtc = bookingOpensAtUtc;
        this.bookingClosesAtUtc = bookingClosesAtUtc;
        this.startTimeUtc = startTimeUtc;
    }

    public void applyStatus(String status) {
        this.status = status;
    }

    public UUID getEventId() { return eventId; }
    public String getStatus() { return status; }
    public Instant getBookingOpensAtUtc() { return bookingOpensAtUtc; }
    public Instant getBookingClosesAtUtc() { return bookingClosesAtUtc; }
    public Instant getStartTimeUtc() { return startTimeUtc; }
}
