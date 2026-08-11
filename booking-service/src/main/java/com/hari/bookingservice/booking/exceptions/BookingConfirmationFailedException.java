package com.hari.bookingservice.booking.exceptions;

import java.util.UUID;

public class BookingConfirmationFailedException extends RuntimeException {
    public BookingConfirmationFailedException(UUID holdId, String reason) {
        super("Cannot confirm booking for hold " + holdId + ": " + reason);
    }
}
