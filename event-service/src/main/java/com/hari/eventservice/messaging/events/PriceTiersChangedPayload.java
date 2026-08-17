package com.hari.eventservice.messaging.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PriceTiersChangedPayload(UUID eventId, List<Tier> tiers) {
    public record Tier(BigDecimal price, String currency) {
    }
}