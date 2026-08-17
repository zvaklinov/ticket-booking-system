package com.hari.bookingservice.outbox.events;

import java.util.List;
import java.util.UUID;

public record SeatHoldExpiredPayload(
        UUID holdId, UUID eventId, UUID userId, List<UUID> seatIds, String reason) {}
