package com.hari.bookingservice.seathold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class EventCancellationCascade {

    private static final Logger log = LoggerFactory.getLogger(EventCancellationCascade.class);

    /** Safety stop: prevents an unbounded loop if a hold somehow refuses to leave ACTIVE. */
    private static final int MAX_BATCHES = 1000;

    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldExpirer expirer;
    private final int batchSize;

    public EventCancellationCascade(SeatHoldRepository seatHoldRepository,
                                    SeatHoldExpirer expirer,
                                    @Value("${booking.sweep.batch-size}") int batchSize) {
        this.seatHoldRepository = seatHoldRepository;
        this.expirer = expirer;
        this.batchSize = batchSize;
    }

    /**
     * Releases every live hold for a cancelled event.
     *
     * Each hold is released in its own REQUIRES_NEW transaction rather than one transaction for
     * the whole event — a popular event could have thousands of live holds, and holding locks on
     * all of them at once would block every concurrent claim attempt.
     *
     * Naturally idempotent: the expirer re-checks each hold's status inside its own transaction,
     * so a redelivered EventCancelled simply finds nothing left to do.
     */
    public void releaseHoldsFor(UUID eventId) {
        int released = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<SeatHold> liveHolds = seatHoldRepository.findByEventIdAndStatusIn(
                    eventId,
                    List.of(SeatHoldStatus.ACTIVE, SeatHoldStatus.PAYMENT_PENDING),
                    PageRequest.of(0, batchSize));

            if (liveHolds.isEmpty()) {
                break;
            }

            for (SeatHold hold : liveHolds) {
                try {
                    expirer.expire(hold.getId(), HoldExpiryReason.EVENT_CANCELLED);
                    released++;
                } catch (RuntimeException e) {
                    // One problematic hold must not abandon the rest. The expiration sweep
                    // will pick it up later since it is still past or approaching its deadline.
                    log.warn("Failed to release hold {} during cancellation of event {}",
                            hold.getId(), eventId, e);
                }
            }
        }

        if (released > 0) {
            log.info("Released {} hold(s) after cancellation of event {}", released, eventId);
        }
    }
}