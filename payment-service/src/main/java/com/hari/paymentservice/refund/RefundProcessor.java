package com.hari.paymentservice.refund;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class RefundProcessor {

    private static final Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    private final PaymentRepository paymentRepository;
    private final RefundExecutor executor;
    private final Clock clock;
    private final Duration resubmitAfter;
    private final Duration retryAfter;
    private final int maxAttempts;
    private final int batchSize;

    public RefundProcessor(PaymentRepository paymentRepository,
                           RefundExecutor executor,
                           Clock clock,
                           @Value("${payment.refund.resubmit-after}") Duration resubmitAfter,
                           @Value("${payment.refund.retry-after}") Duration retryAfter,
                           @Value("${payment.refund.max-attempts}") int maxAttempts,
                           @Value("${payment.refund.batch-size}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.executor = executor;
        this.clock = clock;
        this.resubmitAfter = resubmitAfter;
        this.retryAfter = retryAfter;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.refund.poll-interval-ms}")
    public void submitPendingRefunds() {
        Instant now = clock.instant();

        List<UUID> claimed = paymentRepository.claimRefundsToSubmit(
                now, now.minus(resubmitAfter), batchSize);

        for (UUID paymentId : claimed) {
            try {
                executor.submit(paymentId);
            } catch (RuntimeException e) {
                log.warn("Unexpected failure submitting refund for {}", paymentId, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.refund.poll-interval-ms}")
    public void retryFailedRefunds() {
        List<Payment> failed = paymentRepository
                .findByStatusAndRefundAttemptCountLessThanAndUpdatedAtBefore(
                        PaymentStatus.REFUND_FAILED,
                        maxAttempts,
                        clock.instant().minus(retryAfter),
                        PageRequest.of(0, batchSize));

        for (Payment payment : failed) {
            try {
                executor.retry(payment.getId());
            } catch (RuntimeException e) {
                log.warn("Unexpected failure retrying refund for {}", payment.getId(), e);
            }
        }
    }
}