package com.hari.bookingservice.messaging.events;

import java.util.UUID;

public record EventCancelledPayload(UUID eventId, String status) {}