package com.hari.bookingservice.messaging.events;

import java.util.UUID;

public record EventArchivedPayload(UUID eventId, String status) {}