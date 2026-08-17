package com.hari.bookingservice.messaging.events;

import java.util.UUID;

public record EventStatusChangedPayload(UUID eventId, String status) {}