package com.hari.paymentservice.messaging;

import com.hari.paymentservice.outbox.OutboxWriter;
import com.hari.paymentservice.outbox.events.PaymentLifecyclePayload;
import com.hari.paymentservice.payment.Payment;
import com.hari.paymentservice.payment.PaymentRepository;
import com.hari.paymentservice.payment.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.hari.paymentservice.payment.PaymentStatus.DEAD;

@Component
public class BookingLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycleHandler.class);

    private static final List<PaymentStatus> REFUNDABLE =
            List.of(PaymentStatus.SUCCEEDED, PaymentStatus.REFUND_FAILED);

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OutboxWriter outboxWriter;

    public BookingLifecycleHandler(PaymentRepository paymentRepository,
                                   ProcessedMessageRepository processedMessageRepository,
                                   OutboxWriter outboxWriter) {
        this.paymentRepository = paymentRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void handle(IncomingEnvelope envelope) {
        if (processedMessageRepository.existsById(envelope.eventId())) {
            log.debug("Skipping already-processed message {}", envelope.eventId());
            return;
        }

        switch (envelope.eventType()) {
            case "BookingConfirmationFailed" -> requestRefund(envelope, resolveByPaymentId(envelope));
            case "BookingCancellationRequested" -> requestRefund(envelope, resolveByHoldId(envelope));
            default -> log.debug("Ignoring booking event type {}", envelope.eventType());
        }

        processedMessageRepository.save(
                new ProcessedMessage(envelope.eventId(), envelope.eventType()));
    }

    private Payment resolveByPaymentId(IncomingEnvelope envelope) {
        UUID paymentId = UUID.fromString(envelope.payload().get("paymentId").asString());

        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException(
                        "No payment " + paymentId + " for event " + envelope.eventType()));
    }

    private Payment resolveByHoldId(IncomingEnvelope envelope) {
        UUID holdId = UUID.fromString(envelope.payload().get("holdId").asString());

        return paymentRepository.findByHoldIdForUpdate(holdId, DEAD)
                .orElseThrow(() -> new IllegalStateException(
                        "No live payment for hold " + holdId + " (event " + envelope.eventType() + ")"));
    }

    private void requestRefund(IncomingEnvelope envelope, Payment payment) {
        if (payment.getStatus().isInFlight()) {
            throw new IllegalStateException(
                    "Refund requested for in-flight payment " + payment.getId()
                            + " in status " + payment.getStatus());
        }

        if (payment.getStatus() != PaymentStatus.SUCCEEDED
                && payment.getStatus() != PaymentStatus.REFUND_FAILED) {
            log.info("Ignoring refund request for payment {} in status {}",
                    payment.getId(), payment.getStatus());
            return;
        }

        payment.startRefund();

        outboxWriter.write("RefundRequested", payment.getId(), new PaymentLifecyclePayload(
                payment.getId(), payment.getHoldId(), payment.getUserId(),
                payment.getAmount(), payment.getCurrency()));

        log.info("Refund requested for payment {} due to {}", payment.getId(), envelope.eventType());
    }
}