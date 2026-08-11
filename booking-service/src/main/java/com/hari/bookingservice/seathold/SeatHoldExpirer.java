package com.hari.bookingservice.seathold;

import com.hari.bookingservice.seat.SeatRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
class SeatHoldExpirer {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    SeatHoldExpirer(SeatHoldRepository seatHoldRepository,
                    SeatRepository seatRepository,
                    Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    /**
     * REQUIRES_NEW so each hold commits independently: one problematic hold cannot roll back
     * the rest of the batch, and no transaction stays open for the whole sweep.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expire(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId).orElse(null);
        if (hold == null) {
            return;
        }

        // Re-check the status inside this transaction. The hold may have been confirmed or released
        // between the sweep's read and now — the earlier query result is only a candidate list.
        if (hold.getStatus() != SeatHoldStatus.ACTIVE
                && hold.getStatus() != SeatHoldStatus.PAYMENT_PENDING) {
            return;
        }

        hold.expire(clock.instant());
        seatRepository.releaseSeatsForHold(holdId);

        // TODO Phase 4: publish SeatHoldExpired via the outbox, in this same transaction.
    }
}