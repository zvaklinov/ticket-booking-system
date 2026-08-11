package com.hari.bookingservice.seathold;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "seat_hold_item")
public class SeatHoldItem {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "seat_hold_id", nullable = false)
    private UUID seatHoldId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    protected SeatHoldItem() {
    }

    public SeatHoldItem(UUID seatHoldId, UUID seatId, BigDecimal price, String currency) {
        this.seatHoldId = seatHoldId;
        this.seatId = seatId;
        this.price = price;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public UUID getSeatHoldId() { return seatHoldId; }
    public UUID getSeatId() { return seatId; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
}
