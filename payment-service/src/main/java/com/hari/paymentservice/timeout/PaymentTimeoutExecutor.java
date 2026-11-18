package com.hari.paymentservice.timeout;

import com.hari.paymentservice.outbox.OutboxWriter;
import com.hari.paymentservice.outbox.events.PaymentLifecyclePayload;
import com.hari.paymentservice.payment.Payment;
import com.hari.paymentservice.payment.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
class PaymentTimeoutExecutor {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutExecutor.class);

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    PaymentTimeoutExecutor(PaymentRepository paymentRepository,
                           OutboxWriter outboxWriter,
                           Clock clock) {
        this.paymentRepository = paymentRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expire(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);

        if (payment == null || !payment.getStatus().isInFlight()) {
            return;
        }

        payment.markFailed(clock.instant());

        outboxWriter.write("PaymentFailed", payment.getId(), new PaymentLifecyclePayload(
                payment.getId(), payment.getHoldId(), payment.getUserId(),
                payment.getAmount(), payment.getCurrency()));

        log.info("Payment {} timed out and was marked FAILED", paymentId);
    }
}