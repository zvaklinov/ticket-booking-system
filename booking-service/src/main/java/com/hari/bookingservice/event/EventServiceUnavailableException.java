package com.hari.bookingservice.event;

import java.util.UUID;

public class EventServiceUnavailableException extends RuntimeException {

    public EventServiceUnavailableException(UUID eventId, Throwable cause) {
        super("Event Service unreachable while validating event: " + eventId, cause);
    }
}
