package com.hari.bookingservice.seat.exceptions;

import com.hari.bookingservice.seat.SeatStatus;

public class InvalidSeatTransitionException extends RuntimeException {

    public InvalidSeatTransitionException(SeatStatus from, SeatStatus to) {
        super("Invalid seat transition: " + from + " -> " + to);
    }
}
