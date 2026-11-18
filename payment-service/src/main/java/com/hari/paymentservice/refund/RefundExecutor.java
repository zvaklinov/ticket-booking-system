package com.hari.paymentservice.refund;

import com.hari.paymentservice.payment.Payment;
import com.hari.paymentservice.payment.PaymentRepository;
import com.stripe.exception.StripeException;
import com.hari.paymentservice.stripe.StripeGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
class RefundExecutor {

    private static final Logger log = LoggerFactory.getLogger(RefundExecutor.class);

    private final PaymentRepository paymentRepository;
    private final StripeGateway stripeGateway;
    private final Clock clock;

    RefundExecutor(PaymentRepository paymentRepository, StripeGateway stripeGateway, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.stripeGateway = stripeGateway;
        this.clock = clock;
    }

    /**
     * REQUIRES_NEW so one problematic refund cannot roll back a whole batch.
     * Note what this does NOT do: mark the payment REFUNDED. Stripe accepting the request is
     * not the same as the money having moved — the refund webhook is what confirms that, the
     * same rule that governs payment success.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void submit(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return;
        }

        try {
            stripeGateway.createRefund(payment.getId(), payment.getStripePaymentIntentId());
            log.info("Refund submitted to Stripe for payment {}", paymentId);

        } catch (StripeException e) {
            // Seats stay unreleased. A failed refund must not release
            // seats, because the customer has not actually been made whole.
            payment.markRefundFailed();
            log.error("Refund submission failed for payment {} (attempt {})",
                    paymentId, payment.getRefundAttemptCount(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void retry(UUID paymentId) {
        paymentRepository.findById(paymentId).ifPresent(Payment::startRefund);
    }
}