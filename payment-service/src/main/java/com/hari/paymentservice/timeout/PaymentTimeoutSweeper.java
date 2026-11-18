package com.hari.paymentservice.timeout;

import com.hari.paymentservice.payment.Payment;
import com.hari.paymentservice.payment.PaymentRepository;
import com.hari.paymentservice.payment.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Component
public class PaymentTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutSweeper.class);

    private static final List<PaymentStatus> IN_FLIGHT = List.of(
            PaymentStatus.CREATED,
            PaymentStatus.REQUIRES_PAYMENT_METHOD,
            PaymentStatus.REQUIRES_ACTION,
            PaymentStatus.PROCESSING);

    private final PaymentRepository paymentRepository;
    private final PaymentTimeoutExecutor executor;
    private final Clock clock;
    private final Duration deadline;
    private final int batchSize;

    public PaymentTimeoutSweeper(PaymentRepository paymentRepository,
                                 PaymentTimeoutExecutor executor,
                                 Clock clock,
                                 @Value("${payment.timeout.deadline}") Duration deadline,
                                 @Value("${payment.timeout.batch-size}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.executor = executor;
        this.clock = clock;
        this.deadline = deadline;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.timeout.poll-interval-ms}")
    public void expireStalePayments() {
        List<Payment> stale = paymentRepository.findByStatusInAndCreatedAtBefore(
                IN_FLIGHT, clock.instant().minus(deadline), PageRequest.of(0, batchSize));

        for (Payment payment : stale) {
            try {
                executor.expire(payment.getId());
            } catch (RuntimeException e) {
                log.warn("Unexpected failure expiring payment {}", payment.getId(), e);
            }
        }
    }
}