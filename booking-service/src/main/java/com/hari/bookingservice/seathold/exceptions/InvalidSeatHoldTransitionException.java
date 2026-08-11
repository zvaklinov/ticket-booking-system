package com.hari.bookingservice.seathold.exceptions;

import com.hari.bookingservice.seathold.SeatHoldStatus;

public class InvalidSeatHoldTransitionException extends RuntimeException {

    public InvalidSeatHoldTransitionException(SeatHoldStatus from, SeatHoldStatus to) {
        super("Invalid seat hold transition: " + from + " -> " + to);
    }
}
