package com.hari.bookingservice.seat.dto;

import com.hari.bookingservice.seat.Seat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SeatResponse(
        UUID id,
        UUID eventId,
        String seatLabel,
        BigDecimal price,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getEventId(),
                seat.getSeatLabel(),
                seat.getPrice(),
                seat.getCurrency(),
                seat.getStatus().name(),
                seat.getCreatedAt(),
                seat.getUpdatedAt()
        );
    }
}
