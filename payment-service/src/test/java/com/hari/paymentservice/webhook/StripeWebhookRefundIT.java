package com.hari.paymentservice.webhook;

import com.hari.paymentservice.TestcontainersConfiguration;
import com.hari.paymentservice.outbox.OutboxEntry;
import com.hari.paymentservice.outbox.OutboxRepository;
import com.hari.paymentservice.payment.Payment;
import com.hari.paymentservice.payment.PaymentRepository;
import com.hari.paymentservice.payment.PaymentStatus;
import com.stripe.Stripe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StripeWebhookRefundIT {

    @Autowired private StripeWebhookService webhookService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${stripe.webhook-secret}") private String webhookSecret;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE payment, outbox, stripe_webhook_event, processed_message CASCADE");
    }

    @Test
    void chargeRefundedMarksThePaymentRefundedAndPublishesRefundSucceeded() {
        Payment payment = pendingRefund();
        String body = chargeRefunded(eventId(), payment.getStripePaymentIntentId());

        webhookService.process(body, sign(body));

        assertEquals(PaymentStatus.REFUNDED, reload(payment).getStatus());

        List<OutboxEntry> entries = outboxRepository.findAll();
        assertEquals(1, entries.size());
        assertEquals("RefundSucceeded", entries.get(0).getEventType());
    }

    @Test
    void aRefundUpdatedCarryingStatusSucceededAlsoCompletesTheRefund() {
        Payment payment = pendingRefund();
        String body = refund(eventId(), "refund.updated",
                payment.getStripePaymentIntentId(), "succeeded");

        webhookService.process(body, sign(body));

        assertEquals(PaymentStatus.REFUNDED, reload(payment).getStatus());
        assertEquals("RefundSucceeded", outboxRepository.findAll().get(0).getEventType());
    }

    @Test
    void aRefundUpdatedCarryingStatusFailedMarksTheRefundFailed() {
        Payment payment = pendingRefund();
        String body = refund(eventId(), "refund.updated",
                payment.getStripePaymentIntentId(), "failed");

        webhookService.process(body, sign(body));

        assertEquals(PaymentStatus.REFUND_FAILED, reload(payment).getStatus());
        assertEquals("RefundFailed", outboxRepository.findAll().get(0).getEventType());
    }

    @Test
    void aPendingRefundEventChangesNothing() {
        Payment payment = pendingRefund();
        String body = refund(eventId(), "refund.created",
                payment.getStripePaymentIntentId(), "pending");

        webhookService.process(body, sign(body));

        assertEquals(PaymentStatus.REFUND_PENDING, reload(payment).getStatus());
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void aRedeliveredStripeEventIsSkipped() {
        Payment payment = pendingRefund();
        String body = chargeRefunded(eventId(), payment.getStripePaymentIntentId());
        String signature = sign(body);

        webhookService.process(body, signature);
        webhookService.process(body, signature);

        assertEquals(1, outboxRepository.count());
        assertEquals(PaymentStatus.REFUNDED, reload(payment).getStatus());
    }

    @Test
    void aRefundEventForAnUnknownPaymentIntentThrows() {
        String body = chargeRefunded(eventId(), "pi_does_not_exist");
        String signature = sign(body);

        assertThrows(IllegalStateException.class, () -> webhookService.process(body, signature));
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void aTamperedPayloadIsRejected() {
        Payment payment = pendingRefund();
        String body = chargeRefunded(eventId(), payment.getStripePaymentIntentId());
        String signature = sign(body);

        assertThrows(InvalidWebhookSignatureException.class,
                () -> webhookService.process(body.replace("charge.refunded", "refund.failed"), signature));
    }
    @Test
    void thePairOfEventsStripeSendsForOneSuccessfulRefundPublishesOnce() {
        Payment payment = pendingRefund();
        String first = chargeRefunded(eventId(), payment.getStripePaymentIntentId());
        String second = refund(eventId(), "refund.updated",
                payment.getStripePaymentIntentId(), "succeeded");

        webhookService.process(first, sign(first));
        webhookService.process(second, sign(second));

        assertEquals(PaymentStatus.REFUNDED, reload(payment).getStatus());
        assertEquals(1, outboxRepository.count());
    }

    @Test
    void thePairOfEventsStripeSendsForOneFailedRefundPublishesOnce() {
        Payment payment = pendingRefund();
        String first = refund(eventId(), "refund.updated",
                payment.getStripePaymentIntentId(), "failed");
        String second = refund(eventId(), "refund.failed",
                payment.getStripePaymentIntentId(), "failed");

        webhookService.process(first, sign(first));
        webhookService.process(second, sign(second));

        assertEquals(PaymentStatus.REFUND_FAILED, reload(payment).getStatus());
        assertEquals(1, outboxRepository.count());
    }


    private Payment pendingRefund() {
        Payment payment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), "EUR");
        payment.attachPaymentIntent("pi_" + UUID.randomUUID(), PaymentStatus.PROCESSING);
        payment.applyWebhookStatus(PaymentStatus.SUCCEEDED, Instant.now());
        payment.startRefund();
        return paymentRepository.save(payment);
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private String eventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String chargeRefunded(String eventId, String paymentIntentId) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":%d,
                 "type":"charge.refunded",
                 "data":{"object":{"id":"ch_test","object":"charge","payment_intent":"%s"}}}
                """.formatted(eventId, Stripe.API_VERSION,
                Instant.now().getEpochSecond(), paymentIntentId);
    }

    private String refund(String eventId, String type, String paymentIntentId, String status) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":%d,
                 "type":"%s",
                 "data":{"object":{"id":"re_test","object":"refund",
                                   "payment_intent":"%s","status":"%s"}}}
                """.formatted(eventId, Stripe.API_VERSION,
                Instant.now().getEpochSecond(), type, paymentIntentId, status);
    }

    private String sign(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(
                    (timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("could not sign test payload", e);
        }
    }
}