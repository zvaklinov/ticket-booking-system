package com.hari.bookingservice.messaging;

import com.hari.bookingservice.event.EventBookability;
import com.hari.bookingservice.event.EventBookabilityRepository;
import com.hari.bookingservice.messaging.events.EventPublishedPayload;
import com.hari.bookingservice.messaging.events.EventStatusChangedPayload;
import com.hari.bookingservice.seathold.EventCancellationCascade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EventLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(EventLifecycleHandler.class);

    private final EventBookabilityRepository bookabilityRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final EventCancellationCascade cancellationCascade;
    private final JsonMapper jsonMapper;

    public EventLifecycleHandler(EventBookabilityRepository bookabilityRepository,
                                 ProcessedMessageRepository processedMessageRepository,
                                 EventCancellationCascade cancellationCascade,
                                 JsonMapper jsonMapper) {
        this.bookabilityRepository = bookabilityRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.cancellationCascade = cancellationCascade;
        this.jsonMapper = jsonMapper;
    }

    /**
     * The idempotency marker and the projection update share one transaction. If the update
     * fails, the marker rolls back too, so redelivery genuinely retries rather than being
     * skipped as "already processed".
     */
    @Transactional
    public void handle(EventEnvelope envelope) {
        if (processedMessageRepository.existsById(envelope.eventId())) {
            log.debug("Skipping already-processed message {} ({})",
                    envelope.eventId(), envelope.eventType());
            return;
        }

        switch (envelope.eventType()) {
            case "EventPublished" -> applyPublished(envelope);
            case "EventUpdated" -> applyPublished(envelope);
            case "EventCancelled", "EventArchived" -> applyStatusChange(envelope);
            default -> {
                log.debug("Ignoring unhandled event type {}", envelope.eventType());
            }
        }

        processedMessageRepository.save(
                new ProcessedMessage(envelope.eventId(), envelope.eventType()));
    }

    private void applyPublished(EventEnvelope envelope) {
        EventPublishedPayload payload =
                jsonMapper.treeToValue(envelope.payload(), EventPublishedPayload.class);

        EventBookability existing = bookabilityRepository.findById(payload.eventId()).orElse(null);

        if (existing == null) {
            bookabilityRepository.save(new EventBookability(
                    payload.eventId(),
                    payload.status(),
                    payload.bookingOpensAtUtc(),
                    payload.bookingClosesAtUtc(),
                    payload.startTimeUtc()));
        } else {
            existing.apply(
                    payload.status(),
                    payload.bookingOpensAtUtc(),
                    payload.bookingClosesAtUtc(),
                    payload.startTimeUtc());
        }

        log.info("Event {} is now {} in the local projection", payload.eventId(), payload.status());
    }

    private void applyStatusChange(EventEnvelope envelope) {
        EventStatusChangedPayload payload =
                jsonMapper.treeToValue(envelope.payload(), EventStatusChangedPayload.class);

        bookabilityRepository.findById(payload.eventId()).ifPresentOrElse(
                bookability -> bookability.applyStatus(payload.status()),
                () -> log.warn("Received {} for unknown event {} — ignoring",
                        envelope.eventType(), payload.eventId()));

        // Cancellation additionally releases every live hold. Archival does not: an event is only
        // archivable once concluded or already cancelled, so there is nothing live left to release.
        if ("EventCancelled".equals(envelope.eventType())) {
            cancellationCascade.releaseHoldsFor(payload.eventId());
        }
    }
}