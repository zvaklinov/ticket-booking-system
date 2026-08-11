package com.hari.bookingservice.event;

import java.util.UUID;

public record EventSummary(UUID id, String status) {
}
