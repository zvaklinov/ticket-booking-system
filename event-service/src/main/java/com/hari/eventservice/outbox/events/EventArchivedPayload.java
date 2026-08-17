package com.hari.eventservice.outbox.events;

import java.util.UUID;

public record EventArchivedPayload(UUID eventId, String status) {}