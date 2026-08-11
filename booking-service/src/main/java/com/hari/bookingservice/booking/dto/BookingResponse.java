package com.hari.bookingservice.booking.dto;

import com.hari.bookingservice.booking.Booking;
import com.hari.bookingservice.booking.BookingItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant confirmedAt,
        Instant cancelledAt,
        List<UUID> seatIds
) {
    public static BookingResponse from(Booking booking, List<BookingItem> items) {
        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getUserId(),
                booking.getStatus().name(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getConfirmedAt(),
                booking.getCancelledAt(),
                items.stream().map(BookingItem::getSeatId).toList());
    }
}