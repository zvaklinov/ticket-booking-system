package com.hari.bookingservice.seathold;

import com.hari.bookingservice.TestcontainersConfiguration;
import com.hari.bookingservice.event.EventBookability;
import com.hari.bookingservice.event.EventBookabilityRepository;
import com.hari.bookingservice.seat.Seat;
import com.hari.bookingservice.seat.SeatRepository;
import com.hari.bookingservice.seat.SeatStatus;
import com.hari.bookingservice.seathold.dto.CreateSeatHoldRequest;
import com.hari.bookingservice.seathold.dto.SeatHoldResponse;
import com.hari.bookingservice.seathold.exceptions.SeatsNotAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SeatHoldConcurrencyIT {

    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private SeatHoldItemRepository seatHoldItemRepository;
    @Autowired private EventBookabilityRepository eventBookabilityRepository;
    @Autowired private SeatHoldService seatHoldService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        // Deliberately NOT @Transactional: a test-managed transaction would put every thread
        // in the same transaction, and the race under test could never occur.
        jdbcTemplate.execute("TRUNCATE seat_hold_item, booking_item, booking, seat_hold, seat, "
                + "event_bookability, idempotency_key CASCADE");

        eventId = UUID.randomUUID();
        Instant now = Instant.now();
        eventBookabilityRepository.save(new EventBookability(
                eventId,
                "PUBLISHED",
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.plus(31, ChronoUnit.DAYS)));
    }

    @Test
    void onlyOneOfManyConcurrentClaimsOnTheSameSeatSucceeds() {
        Seat seat = createSeat("A1", "50.00");
        int threads = 10;

        Results results = runConcurrently(threads, i -> seatHoldService.create(
                new CreateSeatHoldRequest(eventId, List.of(seat.getId())),
                UUID.randomUUID(),                    // distinct user per thread
                UUID.randomUUID().toString()));

        assertEquals(1, results.successes(), "exactly one claim should win");
        assertEquals(threads - 1, results.failureCount(), "every other claim should be rejected");
        results.assertAllFailuresAre(SeatsNotAvailableException.class);

        Seat reloaded = seatRepository.findById(seat.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, reloaded.getStatus());
        assertNotNull(reloaded.getActiveHoldId());
        assertEquals(1, seatHoldRepository.count());
        assertEquals(1, seatHoldItemRepository.count());
    }

    @Test
    void overlappingClaimsLeaveNoPartialHold() {
        Seat a1 = createSeat("A1", "50.00");
        Seat a2 = createSeat("A2", "50.00");
        Seat a3 = createSeat("A3", "50.00");   // contested
        Seat a4 = createSeat("A4", "50.00");
        Seat a5 = createSeat("A5", "50.00");

        List<List<UUID>> requests = List.of(
                List.of(a1.getId(), a2.getId(), a3.getId()),
                List.of(a3.getId(), a4.getId(), a5.getId()));

        Results results = runConcurrently(2, i -> seatHoldService.create(
                new CreateSeatHoldRequest(eventId, requests.get(i)),
                UUID.randomUUID(),
                UUID.randomUUID().toString()));

        assertEquals(1, results.successes());
        assertEquals(1, results.failureCount());
        results.assertAllFailuresAre(SeatsNotAvailableException.class);

        long heldCount = seatRepository.findByEventIdOrderBySeatLabelAsc(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.HELD)
                .count();
        assertEquals(3, heldCount, "only the winning hold's seats should be HELD");

        assertEquals(1, seatHoldRepository.count(), "the losing hold row must be rolled back");
        assertEquals(3, seatHoldItemRepository.count());

        List<UUID> distinctHoldIds = seatRepository.findByEventIdOrderBySeatLabelAsc(eventId).stream()
                .map(Seat::getActiveHoldId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        assertEquals(1, distinctHoldIds.size());
    }

    @Test
    void oneActiveHoldPerUserPerEventIsEnforcedUnderConcurrency() {
        Seat a1 = createSeat("A1", "50.00");
        Seat a2 = createSeat("A2", "50.00");
        UUID userId = UUID.randomUUID();

        List<List<UUID>> requests = List.of(List.of(a1.getId()), List.of(a2.getId()));

        Results results = runConcurrently(2, i -> seatHoldService.create(
                new CreateSeatHoldRequest(eventId, requests.get(i)),
                userId,                               // same user — the partial index must reject one
                UUID.randomUUID().toString()));

        assertEquals(1, results.successes(), "a user must not hold two active holds for one event");
        assertEquals(1, results.failureCount());
        assertEquals(1, seatHoldRepository.count());
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyCreateOneHold() {
        Seat a1 = createSeat("A1", "50.00");
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        CreateSeatHoldRequest request = new CreateSeatHoldRequest(eventId, List.of(a1.getId()));

        Results results = runConcurrently(2, i -> seatHoldService.create(request, userId, idempotencyKey));

        assertEquals(1, seatHoldRepository.count(), "one hold, regardless of how the race resolved");
        assertEquals(2, results.successes(), "both callers should receive the same hold");

        List<UUID> returnedIds = results.responses().stream().map(SeatHoldResponse::id).distinct().toList();
        assertEquals(1, returnedIds.size(), "both callers must see the same hold id");
    }


    private Seat createSeat(String label, String price) {
        return seatRepository.saveAndFlush(
                new Seat(eventId, label, new BigDecimal(price), "EUR"));
    }

    private Results runConcurrently(int threadCount, ThrowingFunction task) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<SeatHoldResponse> responses = new CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        // Every thread blocks here, then all are released at once. Without this,
                        // thread startup cost would stagger them enough to serialise the calls
                        // and the race would never happen.
                        startGate.await();
                        responses.add(task.apply(index));
                        successCount.incrementAndGet();
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            assertTrue(doneGate.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            executor.shutdownNow();
        }

        return new Results(successCount.get(), failures, responses);
    }

    @FunctionalInterface
    private interface ThrowingFunction {
        SeatHoldResponse apply(int index);
    }

    private record Results(int successes, List<Throwable> failures, List<SeatHoldResponse> responses) {

        int failureCount() {
            return failures.size();
        }

        void assertAllFailuresAre(Class<? extends Throwable> expected) {
            List<Throwable> unexpected = new ArrayList<>();
            for (Throwable t : failures) {
                if (!expected.isInstance(t)) {
                    unexpected.add(t);
                }
            }
            assertTrue(unexpected.isEmpty(),
                    () -> "unexpected failure types: " + unexpected);
        }
    }
}