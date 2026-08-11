package com.hari.bookingservice.seat.exceptions;

import java.util.UUID;

public class SeatNotFoundException extends RuntimeException {
    public SeatNotFoundException(UUID seatId) {
        super("Seat not found: " + seatId);
    }
}