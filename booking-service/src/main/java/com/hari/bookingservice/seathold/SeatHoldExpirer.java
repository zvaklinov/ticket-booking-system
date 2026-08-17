package com.hari.bookingservice.seathold;

import com.hari.bookingservice.outbox.OutboxWriter;
import com.hari.bookingservice.outbox.events.SeatHoldExpiredPayload;
import com.hari.bookingservice.seat.SeatRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
class SeatHoldExpirer {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldItemRepository seatHoldItemRepository;
    private final SeatRepository seatRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    SeatHoldExpirer(SeatHoldRepository seatHoldRepository,
                    SeatHoldItemRepository seatHoldItemRepository,
                    SeatRepository seatRepository,
                    OutboxWriter outboxWriter,
                    Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatHoldItemRepository = seatHoldItemRepository;
        this.seatRepository = seatRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * REQUIRES_NEW so each hold commits independently: one problematic hold cannot roll back
     * the rest of the batch, and no transaction stays open for the whole sweep.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expire(UUID holdId, HoldExpiryReason reason) {
        SeatHold hold = seatHoldRepository.findById(holdId).orElse(null);
        if (hold == null) {
            return;
        }
        if (hold.getStatus() != SeatHoldStatus.ACTIVE
                && hold.getStatus() != SeatHoldStatus.PAYMENT_PENDING) {
            return;
        }

        List<UUID> seatIds = seatHoldItemRepository.findBySeatHoldId(holdId).stream()
                .map(SeatHoldItem::getSeatId)
                .toList();

        hold.expire(clock.instant());
        seatRepository.releaseSeatsForHold(holdId);

        outboxWriter.write("SeatHoldExpired", holdId, new SeatHoldExpiredPayload(
                holdId, hold.getEventId(), hold.getUserId(), seatIds, reason.name()));
    }
}