package com.hari.paymentservice.booking.exceptions;

import java.util.UUID;

public class BookingServiceUnavailableException extends RuntimeException {
    public BookingServiceUnavailableException(UUID holdId, Throwable cause) {
        super("Booking Service unreachable while processing hold " + holdId, cause);
    }
}