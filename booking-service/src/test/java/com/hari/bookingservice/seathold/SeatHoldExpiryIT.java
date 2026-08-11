package com.hari.bookingservice.seathold;

import com.hari.bookingservice.TestcontainersConfiguration;
import com.hari.bookingservice.event.EventBookability;
import com.hari.bookingservice.event.EventBookabilityRepository;
import com.hari.bookingservice.seat.Seat;
import com.hari.bookingservice.seat.SeatRepository;
import com.hari.bookingservice.seat.SeatStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the expiration sweep. Fixtures are built directly through the repositories rather
 * than through SeatHoldService, so each hold's expires_at / payment_deadline_at can be set to an
 * exact instant. That avoids overriding booking.hold.duration and avoids Thread.Sleep — the tests
 * are deterministic and run instantly.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SeatHoldExpiryIT {

    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private SeatHoldItemRepository seatHoldItemRepository;
    @Autowired private EventBookabilityRepository eventBookabilityRepository;
    @Autowired private SeatHoldSweeper sweeper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;


    private UUID eventId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE seat_hold_item, booking_item, booking, seat_hold, seat, "
                + "event_bookability, idempotency_key CASCADE");

        eventId = UUID.randomUUID();
        Instant now = Instant.now();
        eventBookabilityRepository.save(new EventBookability(
                eventId, "PUBLISHED",
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.plus(31, ChronoUnit.DAYS)));
    }

    @Test
    void sweepExpiresActiveHoldPastItsExpiryAndReleasesItsSeats() {
        Seat seat = createSeat("A1", "50.00");
        SeatHold hold = createHeldHold(List.of(seat), Instant.now().minusSeconds(60));

        sweeper.sweep();

        SeatHold reloadedHold = seatHoldRepository.findById(hold.getId()).orElseThrow();
        assertEquals(SeatHoldStatus.EXPIRED, reloadedHold.getStatus());
        assertNotNull(reloadedHold.getReleasedAt());

        Seat reloadedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, reloadedSeat.getStatus());
        assertNull(reloadedSeat.getActiveHoldId());
        assertNull(reloadedSeat.getHoldExpiresAt());
    }

    @Test
    void sweepLeavesHoldsThatHaveNotExpiredYetUntouched() {
        Seat seat = createSeat("A1", "50.00");
        SeatHold hold = createHeldHold(List.of(seat), Instant.now().plusSeconds(600));

        sweeper.sweep();

        assertEquals(SeatHoldStatus.ACTIVE, seatHoldRepository.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(SeatStatus.HELD, seatRepository.findById(seat.getId()).orElseThrow().getStatus());
    }

    @Test
    void sweepExpiresPaymentPendingHoldPastItsPaymentDeadline() {
        Seat seat = createSeat("A1", "50.00");
        // expires_at is still in the future: only payment_deadline_at has passed, which is the
        // whole point — a hold in PAYMENT_PENDING is governed by the payment deadline instead.
        SeatHold hold = createHeldHold(List.of(seat), Instant.now().plusSeconds(600));

        hold.reserveForPayment(Instant.now().minusSeconds(30));
        seatHoldRepository.saveAndFlush(hold);

        sweeper.sweep();

        assertEquals(SeatHoldStatus.EXPIRED, seatHoldRepository.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.getId()).orElseThrow().getStatus());
    }

    @Test
    void sweepReleasesEverySeatInAMultiSeatHoldAtomically() {
        Seat a1 = createSeat("A1", "50.00");
        Seat a2 = createSeat("A2", "60.00");
        Seat a3 = createSeat("A3", "70.00");
        createHeldHold(List.of(a1, a2, a3), Instant.now().minusSeconds(60));

        sweeper.sweep();

        List<Seat> seats = seatRepository.findByEventIdOrderBySeatLabelAsc(eventId);
        assertTrue(seats.stream().allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE),
                "every seat in the hold must be released, not just some");
        assertTrue(seats.stream().allMatch(s -> s.getActiveHoldId() == null));
    }

    @Test
    void sweepingTwiceIsIdempotent() {
        Seat seat = createSeat("A1", "50.00");
        SeatHold hold = createHeldHold(List.of(seat), Instant.now().minusSeconds(60));

        sweeper.sweep();
        Instant firstReleasedAt = seatHoldRepository.findById(hold.getId()).orElseThrow().getReleasedAt();

        assertDoesNotThrow(() -> sweeper.sweep());

        SeatHold reloaded = seatHoldRepository.findById(hold.getId()).orElseThrow();
        assertEquals(SeatHoldStatus.EXPIRED, reloaded.getStatus());
        assertEquals(firstReleasedAt, reloaded.getReleasedAt(),
                "an already-expired hold must not be re-expired with a new timestamp");
    }

    @Test
    void sweepDoesNotTouchSeatsBelongingToOtherHolds() {
        Seat expiring = createSeat("A1", "50.00");
        Seat healthy = createSeat("A2", "50.00");

        createHeldHold(List.of(expiring), Instant.now().minusSeconds(60));
        createHeldHold(List.of(healthy), Instant.now().plusSeconds(600));

        sweeper.sweep();

        assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(expiring.getId()).orElseThrow().getStatus());
        assertEquals(SeatStatus.HELD, seatRepository.findById(healthy.getId()).orElseThrow().getStatus());
    }

    // ---------- fixtures ----------

    private Seat createSeat(String label, String price) {
        return seatRepository.saveAndFlush(new Seat(eventId, label, new BigDecimal(price), "EUR"));
    }

    private SeatHold createHeldHold(List<Seat> seats, Instant expiresAt) {
        // Committed in its own transaction: the sweeper runs in REQUIRES_NEW and would not
        // see this data otherwise.
        return transactionTemplate.execute(status -> {
            BigDecimal total = seats.stream()
                    .map(Seat::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            SeatHold hold = seatHoldRepository.saveAndFlush(
                    new SeatHold(eventId, UUID.randomUUID(), expiresAt, total, "EUR"));

            List<UUID> seatIds = seats.stream().map(Seat::getId).sorted().toList();
            int claimed = seatRepository.claimSeats(eventId, seatIds, hold.getId(), expiresAt);
            assertEquals(seatIds.size(), claimed, "fixture setup failed to claim all seats");

            seatHoldItemRepository.saveAll(seats.stream()
                    .map(s -> new SeatHoldItem(hold.getId(), s.getId(), s.getPrice(), s.getCurrency()))
                    .toList());

            return hold;
        });
    }
}