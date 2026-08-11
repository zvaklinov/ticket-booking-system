package com.hari.bookingservice.seathold.exceptions;

import java.util.UUID;

public class ActiveHoldAlreadyExistsException extends RuntimeException {

    public ActiveHoldAlreadyExistsException(UUID userId, UUID eventId) {
        super("User " + userId + " already has an active hold for event " + eventId);
    }
}
