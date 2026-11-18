package com.hari.paymentservice.payment;

import com.hari.paymentservice.payment.exceptions.InvalidPaymentTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTransitionTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:15:30Z");

    @Test
    void aNewPaymentStartsInCreated() {
        assertEquals(PaymentStatus.CREATED, newPayment().getStatus());
    }

    @Test
    void onlyEuroIsAccepted() {
        assertThrows(IllegalArgumentException.class, () -> new Payment(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), "USD"));
    }

    @Test
    void aNegativeAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Payment(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-0.01"), "EUR"));
    }

    @Test
    void reachingSucceededRecordsCompletion() {
        Payment payment = newPayment();

        payment.applyWebhookStatus(PaymentStatus.SUCCEEDED, NOW);

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(NOW, payment.getCompletedAt());
    }

    @Test
    void aWebhookRepeatingTheCurrentStatusChangesNothing() {
        Payment payment = succeeded();

        payment.applyWebhookStatus(PaymentStatus.SUCCEEDED, NOW.plusSeconds(60));

        assertEquals(NOW, payment.getCompletedAt());
    }

    @Test
    void aTerminalPaymentIgnoresNonSuccessWebhooks() {
        Payment payment = newPayment();
        payment.applyWebhookStatus(PaymentStatus.PROCESSING, NOW);
        payment.markFailed(NOW);

        payment.applyWebhookStatus(PaymentStatus.CANCELLED, NOW);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void aTimeoutGiveUpOnlyAppliesToAnInFlightPayment() {
        Payment payment = succeeded();

        assertThrows(InvalidPaymentTransitionException.class, () -> payment.markFailed(NOW));
    }

    @Test
    void markingFailedTwiceIsANoOp() {
        Payment payment = newPayment();
        payment.applyWebhookStatus(PaymentStatus.PROCESSING, NOW);

        payment.markFailed(NOW);
        payment.markFailed(NOW);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void aSucceededPaymentCannotBeCancelled() {
        Payment payment = succeeded();

        assertThrows(InvalidPaymentTransitionException.class, () -> payment.markCancelled(NOW));
    }

    @Test
    void aRefundStartsFromSucceededAndCountsAsTheFirstAttempt() {
        Payment payment = succeeded();

        payment.startRefund();

        assertEquals(PaymentStatus.REFUND_PENDING, payment.getStatus());
        assertEquals(1, payment.getRefundAttemptCount());
        assertNull(payment.getRefundSubmittedAt());
    }

    @Test
    void startingARefundTwiceDoesNotDoubleCountTheAttempt() {
        Payment payment = succeeded();

        payment.startRefund();
        payment.startRefund();

        assertEquals(1, payment.getRefundAttemptCount());
    }

    @Test
    void retryingAFailedRefundClearsTheLeaseAndCountsANewAttempt() {
        Payment payment = refundPending();
        payment.markRefundSubmitted(NOW);
        payment.markRefundFailed();

        payment.startRefund();

        assertEquals(PaymentStatus.REFUND_PENDING, payment.getStatus());
        assertEquals(2, payment.getRefundAttemptCount());
        assertNull(payment.getRefundSubmittedAt());
    }

    @Test
    void aRefundCannotStartFromAnInFlightPayment() {
        Payment payment = newPayment();
        payment.applyWebhookStatus(PaymentStatus.PROCESSING, NOW);

        assertThrows(InvalidPaymentTransitionException.class, payment::startRefund);
    }

    @Test
    void aRefundCannotStartFromARefundedPayment() {
        Payment payment = refundPending();
        payment.markRefunded(NOW);

        assertThrows(InvalidPaymentTransitionException.class, payment::startRefund);
    }

    @Test
    void aRefundCompletesOnlyFromRefundPending() {
        Payment payment = succeeded();

        assertThrows(InvalidPaymentTransitionException.class, () -> payment.markRefunded(NOW));
    }

    @Test
    void markingARefundRefundedTwiceIsANoOp() {
        Payment payment = refundPending();

        payment.markRefunded(NOW);
        payment.markRefunded(NOW.plusSeconds(60));

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    void markingARefundFailedTwiceIsANoOp() {
        Payment payment = refundPending();

        payment.markRefundFailed();
        payment.markRefundFailed();

        assertEquals(PaymentStatus.REFUND_FAILED, payment.getStatus());
    }

    @Test
    void aCompletedRefundCannotBeMarkedFailed() {
        Payment payment = refundPending();
        payment.markRefunded(NOW);

        assertThrows(InvalidPaymentTransitionException.class, payment::markRefundFailed);
    }

    @Test
    void aFailedRefundClearsTheSubmissionLease() {
        Payment payment = refundPending();
        payment.markRefundSubmitted(NOW);

        payment.markRefundFailed();

        assertNull(payment.getRefundSubmittedAt());
    }

    @Test
    void moneyArrivingAfterAFailedPaymentIsAcceptedAsALateSuccess() {
        Payment payment = newPayment();
        payment.applyWebhookStatus(PaymentStatus.PROCESSING, NOW);
        payment.markFailed(NOW);

        payment.markLateSuccess(NOW.plusSeconds(300));

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(NOW.plusSeconds(300), payment.getCompletedAt());
    }

    private Payment newPayment() {
        return new Payment(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), "EUR");
    }

    private Payment succeeded() {
        Payment payment = newPayment();
        payment.attachPaymentIntent("pi_test", PaymentStatus.PROCESSING);
        payment.applyWebhookStatus(PaymentStatus.SUCCEEDED, NOW);
        return payment;
    }

    private Payment refundPending() {
        Payment payment = succeeded();
        payment.startRefund();
        return payment;
    }
}