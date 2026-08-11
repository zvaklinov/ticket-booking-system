package com.hari.bookingservice.booking;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_item")
public class BookingItem {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    protected BookingItem() {
    }

    public BookingItem(UUID bookingId, UUID seatId, BigDecimal price, String currency) {
        this.bookingId = bookingId;
        this.seatId = seatId;
        this.price = price;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getSeatId() { return seatId; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
}