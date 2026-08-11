package com.hari.bookingservice.seathold.exceptions;

import java.util.UUID;

public class SeatHoldAccessDeniedException extends RuntimeException {
    public SeatHoldAccessDeniedException(UUID holdId) {
        super("Not the owner of seat hold: " + holdId);
    }
}
