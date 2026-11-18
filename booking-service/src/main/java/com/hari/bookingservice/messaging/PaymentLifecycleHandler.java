package com.hari.bookingservice.messaging;

import com.hari.bookingservice.messaging.events.PaymentCancelledPayload;
import com.hari.bookingservice.seathold.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentLifecycleHandler.class);

    private final SeatHoldService seatHoldService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final JsonMapper jsonMapper;

    public PaymentLifecycleHandler(SeatHoldService seatHoldService,
                                   ProcessedMessageRepository processedMessageRepository,
                                   JsonMapper jsonMapper) {
        this.seatHoldService = seatHoldService;
        this.processedMessageRepository = processedMessageRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        if (processedMessageRepository.existsById(envelope.eventId())) {
            log.debug("Skipping already-processed message {} ({})",
                    envelope.eventId(), envelope.eventType());
            return;
        }

        switch (envelope.eventType()) {
            case "PaymentCancelled" -> releaseHold(envelope);
            default -> log.debug("Ignoring unhandled event type {}", envelope.eventType());
        }

        processedMessageRepository.save(
                new ProcessedMessage(envelope.eventId(), envelope.eventType()));
    }

    private void releaseHold(EventEnvelope envelope) {
        PaymentCancelledPayload payload =
                jsonMapper.treeToValue(envelope.payload(), PaymentCancelledPayload.class);

        seatHoldService.releaseAfterPaymentCancelled(payload.holdId());

        log.info("Released hold {} after cancellation of payment {}",
                payload.holdId(), payload.paymentId());
    }
}