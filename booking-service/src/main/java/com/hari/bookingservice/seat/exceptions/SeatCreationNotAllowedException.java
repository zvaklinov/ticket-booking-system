package com.hari.bookingservice.seat.exceptions;

import java.util.UUID;

public class SeatCreationNotAllowedException extends RuntimeException {

    public SeatCreationNotAllowedException(UUID eventId, String eventStatus) {
        super("Cannot create seats for event " + eventId + " with status " + eventStatus);
    }
}
