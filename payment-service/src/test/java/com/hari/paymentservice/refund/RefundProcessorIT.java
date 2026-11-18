package com.hari.paymentservice.refund;

import com.hari.paymentservice.TestcontainersConfiguration;
import com.hari.paymentservice.payment.Payment;
import com.hari.paymentservice.payment.PaymentRepository;
import com.hari.paymentservice.payment.PaymentStatus;
import com.hari.paymentservice.stripe.StripeGateway;
import com.stripe.exception.ApiConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefundProcessorIT {

    @Autowired private RefundProcessor refundProcessor;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private StripeGateway stripeGateway;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE payment, outbox, stripe_webhook_event, processed_message CASCADE");
    }

    @Test
    void aPendingRefundIsClaimedAndSubmittedToStripe() throws Exception {
        Payment payment = refundPending();

        refundProcessor.submitPendingRefunds();

        verify(stripeGateway).createRefund(payment.getId(), payment.getStripePaymentIntentId());

        Payment reloaded = reload(payment);
        assertEquals(PaymentStatus.REFUND_PENDING, reloaded.getStatus());
        assertNotNull(reloaded.getRefundSubmittedAt(), "the claim should have taken a lease");
    }

    @Test
    void aClaimedRefundIsNotResubmittedWhileItsLeaseHolds() throws Exception {
        refundPending();

        refundProcessor.submitPendingRefunds();
        refundProcessor.submitPendingRefunds();

        verify(stripeGateway, times(1)).createRefund(any(), any());
    }

    @Test
    void anExpiredLeaseIsClaimedAgain() throws Exception {
        Payment payment = refundPending();

        refundProcessor.submitPendingRefunds();
        backdate("refund_submitted_at", payment.getId(), 20);
        refundProcessor.submitPendingRefunds();

        verify(stripeGateway, times(2)).createRefund(any(), any());
    }

    @Test
    void aStripeFailureMarksTheRefundFailedAndReleasesTheLease() throws Exception {
        Payment payment = refundPending();
        when(stripeGateway.createRefund(any(), any()))
                .thenThrow(new ApiConnectionException("stripe unavailable"));

        refundProcessor.submitPendingRefunds();

        Payment reloaded = reload(payment);
        assertEquals(PaymentStatus.REFUND_FAILED, reloaded.getStatus());
        assertNull(reloaded.getRefundSubmittedAt());
    }

    @Test
    void aFailedRefundIsNotRetriedBeforeTheBackoffElapses() {
        Payment payment = failedRefund(1);

        refundProcessor.retryFailedRefunds();

        Payment reloaded = reload(payment);
        assertEquals(PaymentStatus.REFUND_FAILED, reloaded.getStatus());
        assertEquals(1, reloaded.getRefundAttemptCount());
    }

    @Test
    void aFailedRefundIsRetriedOnceTheBackoffHasElapsed() {
        Payment payment = failedRefund(1);
        backdate("updated_at", payment.getId(), 5);

        refundProcessor.retryFailedRefunds();

        Payment reloaded = reload(payment);
        assertEquals(PaymentStatus.REFUND_PENDING, reloaded.getStatus());
        assertEquals(2, reloaded.getRefundAttemptCount());
        assertNull(reloaded.getRefundSubmittedAt());
    }

    @Test
    void aRefundThatExhaustedItsAttemptsIsNeverRetried() {
        Payment payment = failedRefund(5);
        backdate("updated_at", payment.getId(), 60);

        refundProcessor.retryFailedRefunds();

        Payment reloaded = reload(payment);
        assertEquals(PaymentStatus.REFUND_FAILED, reloaded.getStatus());
        assertEquals(5, reloaded.getRefundAttemptCount());
    }

    private Payment refundPending() {
        Payment payment = succeeded();
        payment.startRefund();
        return paymentRepository.save(payment);
    }

    private Payment failedRefund(int attempts) {
        Payment payment = succeeded();
        for (int i = 0; i < attempts; i++) {
            payment.startRefund();
            payment.markRefundFailed();
        }
        return paymentRepository.save(payment);
    }

    private Payment succeeded() {
        Payment payment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), "EUR");
        payment.attachPaymentIntent("pi_" + UUID.randomUUID(), PaymentStatus.PROCESSING);
        payment.applyWebhookStatus(PaymentStatus.SUCCEEDED, Instant.now());
        return payment;
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private void backdate(String column, UUID paymentId, int minutes) {
        jdbcTemplate.update(
                "UPDATE payment SET " + column + " = " + column + " - (? || ' minutes')::interval "
                        + "WHERE id = ?", minutes, paymentId);
    }
}