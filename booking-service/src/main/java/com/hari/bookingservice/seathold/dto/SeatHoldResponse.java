package com.hari.bookingservice.seathold.dto;

import com.hari.bookingservice.seathold.SeatHold;
import com.hari.bookingservice.seathold.SeatHoldItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatHoldResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        String status,
        Instant expiresAt,
        BigDecimal totalAmount,
        String currency,
        List<UUID> seatIds
) {
    public static SeatHoldResponse from(SeatHold hold, List<SeatHoldItem> items) {
        return new SeatHoldResponse(
                hold.getId(),
                hold.getEventId(),
                hold.getUserId(),
                hold.getStatus().name(),
                hold.getExpiresAt(),
                hold.getTotalAmount(),
                hold.getCurrency(),
                items.stream().map(SeatHoldItem::getSeatId).toList()
        );
    }
}
