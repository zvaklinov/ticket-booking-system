package com.hari.bookingservice.booking.dto;

import java.util.UUID;

public record BookingSummaryResponse(UUID eventId, long confirmedBookingCount) {

    public boolean hasConfirmedBookings() {
        return confirmedBookingCount > 0;
    }
}