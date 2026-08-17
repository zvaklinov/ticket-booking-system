package com.hari.bookingservice.outbox.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Carries the complete current set of price tiers, not "a tier was added".
 *
 * A delta requires the consumer to already hold correct prior state — miss one message and the
 * projection is permanently wrong with no way to notice. A full snapshot lets the consumer
 * replace wholesale, which makes the update idempotent, order-independent, and self-healing:
 * any successfully processed message leaves the projection correct regardless of what came
 * before. The cost is a slightly larger payload, which is irrelevant for a handful of tiers.
 */
public record EventPriceTiersChangedPayload(UUID eventId, List<PriceTier> tiers) {

    public record PriceTier(BigDecimal price, String currency) {
    }
}