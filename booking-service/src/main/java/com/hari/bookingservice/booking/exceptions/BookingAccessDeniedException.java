package com.hari.bookingservice.booking.exceptions;

import java.util.UUID;

public class BookingAccessDeniedException extends RuntimeException {
    public BookingAccessDeniedException(UUID bookingId) {
        super("Not the owner of booking: " + bookingId);
    }
}