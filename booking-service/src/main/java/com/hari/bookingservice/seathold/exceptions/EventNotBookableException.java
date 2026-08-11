package com.hari.bookingservice.seathold.exceptions;

import java.util.UUID;

public class EventNotBookableException extends RuntimeException {

    public EventNotBookableException(UUID eventId, String reason) {
        super("Event " + eventId + " is not bookable: " + reason);
    }
}
