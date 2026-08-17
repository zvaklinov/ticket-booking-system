package com.hari.eventservice.outbox.events;

import java.util.UUID;

public record EventCancelledPayload(UUID eventId, String status) {}