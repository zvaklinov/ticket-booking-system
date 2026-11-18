package com.hari.paymentservice.payment;

import com.hari.paymentservice.common.IdempotencyKey;
import com.hari.paymentservice.common.IdempotencyKeyRepository;
import com.hari.paymentservice.outbox.OutboxWriter;
import com.hari.paymentservice.outbox.events.PaymentCreatedPayload;
import com.hari.paymentservice.payment.exceptions.PaymentNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The transactional half of payment creation. Kept separate from PaymentService so the external
 * calls (Booking Service, Stripe) happen *between* short transactions rather than inside one long
 * one — a database transaction held open across two HTTP calls can last seconds and blocks
 * everything touching those rows.
 */
@Component
class PaymentUnitOfWork {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxWriter outboxWriter;

    PaymentUnitOfWork(PaymentRepository paymentRepository,
                      IdempotencyKeyRepository idempotencyKeyRepository,
                      OutboxWriter outboxWriter) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Claims the hold by inserting the Payment row. The partial unique index on hold_id is what
     * makes this a claim rather than a hope: a concurrent request for the same hold fails here,
     * before either has touched Stripe.
     */
    @Transactional
    Payment createPaymentRecord(UUID holdId, UUID userId, BigDecimal amount, String currency,
                                String idempotencyKey, String requestHash) {
        Payment payment = paymentRepository.saveAndFlush(
                new Payment(holdId, userId, amount, currency));

        idempotencyKeyRepository.save(
                new IdempotencyKey(userId, idempotencyKey, requestHash, payment.getId()));

        return payment;
    }

    /** Records the Stripe PaymentIntent and announces the payment. */
    @Transactional
    Payment attachPaymentIntent(UUID paymentId, String stripePaymentIntentId,
                                PaymentStatus initialStatus) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        payment.attachPaymentIntent(stripePaymentIntentId, initialStatus);

        outboxWriter.write("PaymentCreated", payment.getId(), new PaymentCreatedPayload(
                payment.getId(), payment.getHoldId(), payment.getUserId(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus().name()));

        return payment;
    }
}