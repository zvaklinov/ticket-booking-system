package com.hari.bookingservice.seathold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class SeatHoldSweeper {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldSweeper.class);

    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldExpirer expirer;
    private final Clock clock;
    private final int batchSize;

    public SeatHoldSweeper(SeatHoldRepository seatHoldRepository,
                           SeatHoldExpirer expirer,
                           Clock clock,
                           @Value("${booking.sweep.batch-size}") int batchSize) {
        this.seatHoldRepository = seatHoldRepository;
        this.expirer = expirer;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${booking.sweep.interval-ms}")
    public void sweep() {
        Instant now = clock.instant();
        int expired = 0;

        List<SeatHold> activeExpired = seatHoldRepository
                .findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        SeatHoldStatus.ACTIVE, now, PageRequest.of(0, batchSize));

        List<SeatHold> paymentExpired = seatHoldRepository
                .findByStatusAndPaymentDeadlineAtBeforeOrderByPaymentDeadlineAtAsc(
                        SeatHoldStatus.PAYMENT_PENDING, now, PageRequest.of(0, batchSize));

        for (SeatHold hold : activeExpired) {
            expired += expireQuietly(hold);
        }
        for (SeatHold hold : paymentExpired) {
            expired += expireQuietly(hold);
        }

        if (expired > 0) {
            log.info("Expired {} seat hold(s)", expired);
        }
    }

    private int expireQuietly(SeatHold hold) {
        try {
            expirer.expire(hold.getId());
            return 1;
        } catch (RuntimeException e) {
            // One bad hold must not abort the sweep; the next run will retry it.
            log.warn("Failed to expire seat hold {}", hold.getId(), e);
            return 0;
        }
    }
}