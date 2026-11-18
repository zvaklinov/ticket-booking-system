// com/hari/paymentservice/webhook/StripeWebhookService.java
package com.hari.paymentservice.webhook;

import com.hari.paymentservice.outbox.OutboxWriter;
import com.hari.paymentservice.outbox.events.PaymentLifecyclePayload;
import com.hari.paymentservice.payment.*;
import com.hari.paymentservice.stripe.StripeStatusMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final StripeWebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private final String webhookSecret;

    public StripeWebhookService(StripeWebhookEventRepository webhookEventRepository,
                                PaymentRepository paymentRepository,
                                PaymentAttemptRepository paymentAttemptRepository,
                                OutboxWriter outboxWriter,
                                Clock clock,
                                @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public void process(String payload, String signature) {

        Event event = verify(payload, signature);

        if (webhookEventRepository.existsById(event.getId())) {
            log.debug("Skipping already-received Stripe event {}", event.getId());
            return;
        }

        if (event.getType().startsWith("charge.refund") || event.getType().startsWith("refund.")) {
            handleRefundEvent(event, payload);
            return;
        }

        Optional<PaymentIntent> maybeIntent = extractPaymentIntent(event);

        StripeWebhookEvent record = new StripeWebhookEvent(
                event.getId(),
                event.getType(),
                maybeIntent.map(PaymentIntent::getId).orElse(null),
                payload);
        webhookEventRepository.save(record);

        if (maybeIntent.isEmpty()) {
            record.markProcessed(clock.instant());
            return;
        }

        applyToPayment(event, maybeIntent.get(), record);
    }

    private Event verify(String payload, String signature) {
        try {
            return Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new InvalidWebhookSignatureException(e);
        }
    }

    private void applyToPayment(Event event, PaymentIntent intent, StripeWebhookEvent record) {
        Payment payment = paymentRepository
                .findByStripePaymentIntentIdForUpdate(intent.getId())
                .orElse(null);

        if (payment == null) {
            // Can legitimately happen: Stripe's webhook can arrive before our own transaction
            // recording the PaymentIntent has committed. Recording and acknowledging is wrong
            // here — we want Stripe to retry, so this throws and the controller returns 500.
            throw new IllegalStateException(
                    "No payment found for PaymentIntent " + intent.getId());
        }

        Instant now = clock.instant();

        if ("payment_intent.payment_failed".equals(event.getType())) {
            recordDeclinedAttempt(payment, intent, now);
            record.markProcessed(now);
            return;
        }

        PaymentStatus newStatus = StripeStatusMapper.fromStripeStatus(intent.getStatus());
        PaymentStatus previous = payment.getStatus();

        if (previous.isTerminal() && newStatus == PaymentStatus.SUCCEEDED) {
            // The money arrived after we gave up. It cannot be kept — mark it for refund and let
            // the refund processor perform it, with the same retry semantics as every other refund.
            payment.markLateSuccess(now);
            payment.startRefund();

            outboxWriter.write("PaymentSucceeded", payment.getId(), payloadFor(payment));
            outboxWriter.write("RefundRequested", payment.getId(), payloadFor(payment));

            log.warn("Late success on payment {} — refund pending", payment.getId());
            record.markProcessed(now);
            return;
        }

        payment.applyWebhookStatus(newStatus, now);

        if (newStatus == PaymentStatus.SUCCEEDED && previous != PaymentStatus.SUCCEEDED) {
            outboxWriter.write("PaymentSucceeded", payment.getId(), payloadFor(payment));
        } else if (newStatus == PaymentStatus.CANCELLED && previous != PaymentStatus.CANCELLED) {
            outboxWriter.write("PaymentCancelled", payment.getId(), payloadFor(payment));
        }

        record.markProcessed(now);
    }

    private void recordDeclinedAttempt(Payment payment, PaymentIntent intent, Instant now) {

        long attemptNumber = paymentAttemptRepository.countByPaymentId(payment.getId()) + 1;

        String failureCode = null;
        String failureMessage = null;
        if (intent.getLastPaymentError() != null) {
            failureCode = intent.getLastPaymentError().getCode();
            failureMessage = intent.getLastPaymentError().getMessage();
        }

        paymentAttemptRepository.save(new PaymentAttempt(
                payment.getId(), (int) attemptNumber, intent.getId(),
                intent.getStatus(), failureCode, failureMessage, now));

        log.info("Payment {} attempt {} declined: {}", payment.getId(), attemptNumber, failureCode);
    }

    private Optional<PaymentIntent> extractPaymentIntent(Event event) {

        Optional<StripeObject> object = event.getDataObjectDeserializer().getObject();

        if (object.isPresent() && object.get() instanceof PaymentIntent intent) {
            return Optional.of(intent);
        }

        log.debug("Event {} of type {} carries no usable PaymentIntent",
                event.getId(), event.getType());
        return Optional.empty();
    }

    private PaymentLifecyclePayload payloadFor(Payment payment) {
        return new PaymentLifecyclePayload(
                payment.getId(), payment.getHoldId(), payment.getUserId(),
                payment.getAmount(), payment.getCurrency());
    }

    private void handleRefundEvent(Event event, String payload) {

        StripeObject object = deserialize(event);
        String paymentIntentId = extractPaymentIntentIdFromRefund(object, event);

        StripeWebhookEvent record = new StripeWebhookEvent(
                event.getId(), event.getType(), paymentIntentId, payload);
        webhookEventRepository.save(record);

        Payment payment = paymentRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId)
                .orElseThrow(() -> new IllegalStateException(
                        "No payment for refunded PaymentIntent " + paymentIntentId));

        Instant now = clock.instant();

        if (isRefundSuccess(event, object)) {
            if (payment.getStatus() != PaymentStatus.REFUNDED) {
                payment.markRefunded(now);
                outboxWriter.write("RefundSucceeded", payment.getId(), payloadFor(payment));
            }
        } else if (isRefundFailure(object)) {
            if (payment.getStatus() != PaymentStatus.REFUND_FAILED) {
                payment.markRefundFailed();
                outboxWriter.write("RefundFailed", payment.getId(), payloadFor(payment));
            }
        } else {
            log.debug("Refund event {} for payment {} carries no terminal outcome",
                    event.getType(), payment.getId());
        }

        record.markProcessed(now);
    }

    private StripeObject deserialize(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Could not deserialize data object for Stripe event " + event.getId()
                                + " (API version " + event.getApiVersion() + ")"));
    }

    private String extractPaymentIntentIdFromRefund(StripeObject object, Event event) {
        if (object instanceof Charge charge) {
            return charge.getPaymentIntent();
        }
        if (object instanceof Refund refund) {
            return refund.getPaymentIntent();
        }
        throw new IllegalStateException(
                "Unexpected object type for refund event " + event.getType());
    }

    private boolean isRefundSuccess(Event event, StripeObject object) {
        if (event.getType().equals("charge.refunded")) {
            return true;
        }
        return object instanceof Refund refund && "succeeded".equals(refund.getStatus());
    }

    private boolean isRefundFailure(StripeObject object) {
        return object instanceof Refund refund
                && ("failed".equals(refund.getStatus()) || "canceled".equals(refund.getStatus()));
    }
}