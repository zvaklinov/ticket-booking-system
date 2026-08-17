package com.hari.eventservice.messaging;

import com.hari.eventservice.messaging.events.PriceTiersChangedPayload;
import com.hari.eventservice.pricetier.EventPriceTier;
import com.hari.eventservice.pricetier.EventPriceTierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BookingLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycleHandler.class);

    private final EventPriceTierRepository priceTierRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final JsonMapper jsonMapper;

    public BookingLifecycleHandler(EventPriceTierRepository priceTierRepository,
                                   ProcessedMessageRepository processedMessageRepository,
                                   JsonMapper jsonMapper) {
        this.priceTierRepository = priceTierRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void handle(IncomingEnvelope envelope) {
        if (processedMessageRepository.existsById(envelope.eventId())) {
            log.debug("Skipping already-processed message {}", envelope.eventId());
            return;
        }

        if ("EventPriceTiersChanged".equals(envelope.eventType())) {
            applyPriceTiers(envelope);
        } else {
            // Every other booking event is irrelevant to this service today. Recording it as
            // processed rather than failing keeps the consumer forward-compatible.
            log.debug("Ignoring booking event type {}", envelope.eventType());
        }

        processedMessageRepository.save(
                new ProcessedMessage(envelope.eventId(), envelope.eventType()));
    }

    private void applyPriceTiers(IncomingEnvelope envelope) {
        PriceTiersChangedPayload payload =
                jsonMapper.treeToValue(envelope.payload(), PriceTiersChangedPayload.class);

        // Replace wholesale. Because the event carries the full set, this is idempotent and
        // converges to the correct state no matter how many times it runs or in what order
        // relative to earlier snapshots for the same event.
        priceTierRepository.deleteByEventId(payload.eventId());
        // Hibernate orders inserts before deletes at flush time, so without this the inserts
        // below would collide with the rows we just asked to remove. Flushing here forces the
        // DELETE to hit the database first.
        priceTierRepository.flush();

        payload.tiers().forEach(tier -> priceTierRepository.save(
                new EventPriceTier(payload.eventId(), tier.price(), tier.currency())));

        log.info("Event {} now has {} price tier(s)", payload.eventId(), payload.tiers().size());
    }
}