package com.hari.paymentservice.messaging;

import com.hari.paymentservice.TestcontainersConfiguration;
import com.hari.paymentservice.outbox.OutboxEntry;
import com.hari.paymentservice.outbox.OutboxRepository;
import com.hari.paymentservice.payment.Payment;
import com.hari.paymentservice.payment.PaymentRepository;
import com.hari.paymentservice.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BookingLifecycleHandlerIT {

    @Autowired private BookingLifecycleHandler handler;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private ProcessedMessageRepository processedMessageRepository;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE payment, outbox, processed_message CASCADE");
    }

    @Test
    void confirmationFailureMovesASucceededPaymentToRefundPending() {
        Payment payment = savedPayment(PaymentStatus.SUCCEEDED);

        handler.handle(confirmationFailed(payment));

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_PENDING, reloaded.getStatus());
        assertEquals(1, reloaded.getRefundAttemptCount());
        assertNull(reloaded.getRefundSubmittedAt());

        List<OutboxEntry> entries = outboxRepository.findAll();
        assertEquals(1, entries.size());
        assertEquals("RefundRequested", entries.get(0).getEventType());
    }

    @Test
    void cancellationResolvesThePaymentByHoldRatherThanPaymentId() {
        Payment payment = savedPayment(PaymentStatus.SUCCEEDED);

        handler.handle(cancellationRequested(payment));

        assertEquals(PaymentStatus.REFUND_PENDING,
                paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1, outboxRepository.count());
    }

    @Test
    void aRedeliveredMessageChangesNothing() {
        Payment payment = savedPayment(PaymentStatus.SUCCEEDED);
        IncomingEnvelope envelope = confirmationFailed(payment);

        handler.handle(envelope);
        handler.handle(envelope);

        assertEquals(1, outboxRepository.count());
        assertEquals(1, paymentRepository.findById(payment.getId()).orElseThrow().getRefundAttemptCount());
    }

    @Test
    void aSecondDistinctRefundRequestIsIgnoredOnStatus() {
        Payment payment = savedPayment(PaymentStatus.SUCCEEDED);

        handler.handle(confirmationFailed(payment));
        handler.handle(cancellationRequested(payment));

        assertEquals(1, outboxRepository.count());
        assertEquals(1, paymentRepository.findById(payment.getId()).orElseThrow().getRefundAttemptCount());
    }

    @Test
    void aRefundRequestForAnInFlightPaymentThrows() {
        Payment payment = savedPayment(PaymentStatus.PROCESSING);
        IncomingEnvelope envelope = confirmationFailed(payment);

        assertThrows(IllegalStateException.class, () -> handler.handle(envelope));
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void aRefundRequestForAnUnknownPaymentThrows() {
        IncomingEnvelope envelope = envelope("BookingConfirmationFailed", """
                {"paymentId":"%s","holdId":"%s","eventId":"%s","userId":"%s","reason":"HOLD_EXPIRED"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertThrows(IllegalStateException.class, () -> handler.handle(envelope));
    }

    @Test
    void concurrentRefundRequestsForTheSamePaymentProduceExactlyOneRefund() throws Exception {
        Payment payment = savedPayment(PaymentStatus.SUCCEEDED);

        List<IncomingEnvelope> envelopes =
                List.of(confirmationFailed(payment), cancellationRequested(payment));

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (IncomingEnvelope envelope : envelopes) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        handler.handle(envelope);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startGate.countDown();
            assertTrue(finished.await(20, TimeUnit.SECONDS), "handlers did not finish in time");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(failures.isEmpty(), () -> "handler threw: " + failures);

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_PENDING, reloaded.getStatus());
        assertEquals(1, reloaded.getRefundAttemptCount());
        assertEquals(1, outboxRepository.count());
        assertEquals(2, processedMessageRepository.count());
    }

    @Test
    void anUnknownEventTypeIsRecordedAndIgnored() {
        IncomingEnvelope envelope = envelope("SeatHoldCreated", "{}");

        handler.handle(envelope);

        assertTrue(processedMessageRepository.existsById(envelope.eventId()));
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void anEnvelopeCarryingAnUnknownTopLevelFieldStillDeserialises() {
        Payment payment = savedPayment(PaymentStatus.SUCCEEDED);

        String raw = """
            {"eventId":"%s","eventType":"BookingConfirmationFailed","eventVersion":1,
             "occurredAt":"2026-09-04T10:15:30Z","producer":"booking-service",
             "correlationId":"%s","causationId":null,"aggregateId":"%s",
             "payloadSchemaVersion":1,"addedByANewerProducer":"ignored",
             "payload":{"paymentId":"%s","holdId":"%s","eventId":"%s","userId":"%s",
                        "reason":"HOLD_EXPIRED"}}
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), payment.getHoldId(),
                payment.getId(), payment.getHoldId(), UUID.randomUUID(), payment.getUserId());

        handler.handle(jsonMapper.readValue(raw, IncomingEnvelope.class));

        assertEquals(1, outboxRepository.count());
    }

    private Payment savedPayment(PaymentStatus status) {
        Payment payment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), "EUR");
        payment.attachPaymentIntent("pi_" + UUID.randomUUID(), PaymentStatus.PROCESSING);
        payment.applyWebhookStatus(status, Instant.now());
        return paymentRepository.save(payment);
    }

    private IncomingEnvelope confirmationFailed(Payment payment) {
        return envelope("BookingConfirmationFailed", """
                {"paymentId":"%s","holdId":"%s","eventId":"%s","userId":"%s","reason":"HOLD_EXPIRED"}
                """.formatted(payment.getId(), payment.getHoldId(),
                UUID.randomUUID(), payment.getUserId()));
    }

    private IncomingEnvelope cancellationRequested(Payment payment) {
        return envelope("BookingCancellationRequested", """
                {"bookingId":"%s","holdId":"%s","eventId":"%s","userId":"%s","reason":"CUSTOMER_REQUESTED"}
                """.formatted(UUID.randomUUID(), payment.getHoldId(),
                UUID.randomUUID(), payment.getUserId()));
    }

    private IncomingEnvelope envelope(String eventType, String payloadJson) {
        return new IncomingEnvelope(
                UUID.randomUUID(), eventType, 1, Instant.now(), "booking-service",
                UUID.randomUUID().toString(), null, UUID.randomUUID().toString(), 1,
                jsonMapper.readTree(payloadJson));
    }
}