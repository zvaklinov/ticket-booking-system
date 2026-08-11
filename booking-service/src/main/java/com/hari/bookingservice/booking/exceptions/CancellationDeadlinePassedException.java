package com.hari.bookingservice.booking.exceptions;

import java.util.UUID;

public class CancellationDeadlinePassedException extends RuntimeException {
    public CancellationDeadlinePassedException(UUID bookingId) {
        super("Cancellation deadline has passed for booking " + bookingId);
    }
}
