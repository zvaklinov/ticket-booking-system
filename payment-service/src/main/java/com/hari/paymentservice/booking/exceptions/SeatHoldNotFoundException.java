package com.hari.paymentservice.booking.exceptions;

import java.util.UUID;

public class SeatHoldNotFoundException extends RuntimeException {
    public SeatHoldNotFoundException(UUID holdId) {
        super("Seat hold not found: " + holdId);
    }
}