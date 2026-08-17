package com.hari.eventservice.event;

import com.hari.eventservice.category.Category;
import com.hari.eventservice.common.InvalidEventTransitionException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event")
public class Event {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    private String location;
    private String venueName;
    private String venueTimezone;

    @Column(name = "start_time_utc", nullable = false)
    private Instant startTimeUtc;

    @Column(name = "end_time_utc", nullable = false)
    private Instant endTimeUtc;

    @Column(name = "booking_opens_at_utc", nullable = false)
    private Instant bookingOpensAtUtc;

    @Column(name = "booking_closes_at_utc", nullable = false)
    private Instant bookingClosesAtUtc;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Integer version;

    protected Event() {
        // required by Hibernate
    }

    public Event(Category category, String title, String description, String venueName, String location,
                 String venueTimezone, Instant startTimeUtc, Instant endTimeUtc, Instant bookingOpensAtUtc,
                 Instant bookingClosesAtUtc, String currency) {
        if (!startTimeUtc.isBefore(endTimeUtc)) {
            throw new IllegalArgumentException("startTimeUtc must be before endTimeUtc");
        }
        if (!"EUR".equals(currency)) {
            throw new IllegalArgumentException("currency must be EUR");
        }
        this.category = category;
        this.title = title;
        this.description = description;
        this.venueName = venueName;
        this.location = location;
        this.venueTimezone = venueTimezone;
        this.startTimeUtc = startTimeUtc;
        this.endTimeUtc = endTimeUtc;
        this.bookingOpensAtUtc = bookingOpensAtUtc;
        this.bookingClosesAtUtc = bookingClosesAtUtc;
        this.currency = currency;
        this.status = EventStatus.DRAFT;
    }

    public void publish() {

        if (status == EventStatus.PUBLISHED) return;

        if (status == EventStatus.DRAFT)
        {
            if (venueName == null || location == null || venueTimezone == null
                    || bookingOpensAtUtc.isAfter(bookingClosesAtUtc)
                    || bookingClosesAtUtc.isAfter(startTimeUtc)) {
                throw new InvalidEventTransitionException("Event is missing required fields for publishing");
            }

            status = EventStatus.PUBLISHED;
        }
        else {
            throw new InvalidEventTransitionException("Cannot publish from status " + status);
        }
    }

    public void cancel() {

        if (status == EventStatus.CANCELLED) return;
        if (status == EventStatus.PUBLISHED)
        {
            status = EventStatus.CANCELLED;
        } else {
            throw new InvalidEventTransitionException("Cannot cancel from status " + status);
        }
    }

    public void archive(Instant now) {

        if (status == EventStatus.ARCHIVED) return;

        if (status == EventStatus.DRAFT || status == EventStatus.CANCELLED) {
            status = EventStatus.ARCHIVED;
        } else if (status == EventStatus.PUBLISHED) {
            if (!now.isAfter(endTimeUtc)) {
                throw new InvalidEventTransitionException(
                        "Cannot archive a published event that hasn't concluded yet; cancel it first");
            }
            status = EventStatus.ARCHIVED;
        } else {
            throw new InvalidEventTransitionException("Cannot archive from status " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public String getLocation() {
        return location;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueTimezone() {
        return venueTimezone;
    }

    public Instant getStartTimeUtc() {
        return startTimeUtc;
    }

    public Instant getEndTimeUtc() {
        return endTimeUtc;
    }

    public Instant getBookingOpensAtUtc() {
        return bookingOpensAtUtc;
    }

    public Instant getBookingClosesAtUtc() {
        return bookingClosesAtUtc;
    }

    public String getCurrency() {
        return currency;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }
}
