package com.hari.bookingservice.booking.exceptions;

import com.hari.bookingservice.booking.BookingStatus;

public class InvalidBookingTransitionException extends RuntimeException {
    public InvalidBookingTransitionException(BookingStatus from, BookingStatus to) {
        super("Invalid booking transition: " + from + " -> " + to);
    }
}
