package com.hari.bookingservice.booking;

import com.hari.bookingservice.TestcontainersConfiguration;
import com.hari.bookingservice.event.EventBookability;
import com.hari.bookingservice.event.EventBookabilityRepository;
import com.hari.bookingservice.outbox.OutboxEntry;
import com.hari.bookingservice.outbox.OutboxRepository;
import com.hari.bookingservice.seat.Seat;
import com.hari.bookingservice.seat.SeatRepository;
import com.hari.bookingservice.seat.SeatStatus;
import com.hari.bookingservice.seathold.*;
import com.hari.bookingservice.seathold.dto.CreateSeatHoldRequest;
import com.hari.bookingservice.seathold.dto.SeatHoldResponse;
import com.hari.bookingservice.seathold.exceptions.SeatHoldNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the PAYMENT_PENDING -> CONFIRMED transition, driven in Phase 5 by the
 * PaymentSucceeded Kafka consumer.
 *
 * Note what the failure tests assert: not that an exception was thrown, but that a
 * BookingConfirmationFailed event was published. That event is what triggers the compensating
 * refund — a failure that does not publish it leaves a customer charged for nothing, which is
 * a far worse outcome than an exception nobody sees.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingConfirmationIT {

    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private SeatHoldItemRepository seatHoldItemRepository;
    @Autowired private EventBookabilityRepository eventBookabilityRepository;
    @Autowired private SeatHoldService seatHoldService;
    @Autowired private BookingConfirmationService confirmationService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingItemRepository bookingItemRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID eventId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE seat_hold_item, booking_item, booking, seat_hold, seat, "
                + "event_bookability, idempotency_key, outbox, processed_message CASCADE");

        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        eventBookabilityRepository.save(new EventBookability(
                eventId, "PUBLISHED",
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.plus(31, ChronoUnit.DAYS)));
    }

    // ---------- successful confirmation ----------

    @Test
    void confirmingAPaidHoldCreatesABookingAndBooksTheSeats() {
        Seat a1 = createSeat("A1", "50.00");
        Seat a2 = createSeat("A2", "70.00");
        SeatHoldResponse hold = createHold(List.of(a1, a2));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        ConfirmationResult result = confirmationService.confirmFromPayment(
                UUID.randomUUID(), hold.id(), new BigDecimal("120.00"), "EUR");

        assertTrue(result.isConfirmed());
        Booking booking = result.booking();

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(hold.id(), booking.getSourceHoldId());
        assertEquals(eventId, booking.getEventId());
        assertEquals(userId, booking.getUserId());
        assertEquals(0, new BigDecimal("120.00").compareTo(booking.getTotalAmount()));
        assertNotNull(booking.getConfirmedAt());

        assertEquals(SeatHoldStatus.CONFIRMED,
                seatHoldRepository.findById(hold.id()).orElseThrow().getStatus());

        List<Seat> seats = seatRepository.findByEventIdOrderBySeatLabelAsc(eventId);
        assertTrue(seats.stream().allMatch(s -> s.getStatus() == SeatStatus.BOOKED));
        assertTrue(seats.stream().allMatch(s -> s.getActiveHoldId() == null),
                "a booked seat is no longer held");

        assertEquals(1, countOutboxEvents("BookingConfirmed"));
        assertEquals(0, countOutboxEvents("BookingConfirmationFailed"));
    }

    @Test
    void bookingItemsCarryTheHoldsSnapshottedPricesNotLiveSeatPrices() {
        Seat a1 = createSeat("A1", "50.00");
        Seat a2 = createSeat("A2", "70.00");
        SeatHoldResponse hold = createHold(List.of(a1, a2));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        Booking booking = confirmationService.confirmFromPayment(
                UUID.randomUUID(), hold.id(), new BigDecimal("120.00"), "EUR").booking();

        List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
        assertEquals(2, items.size());

        BigDecimal itemsTotal = items.stream()
                .map(BookingItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, booking.getTotalAmount().compareTo(itemsTotal),
                "booking total must equal the sum of its item snapshots");

        List<SeatHoldItem> holdItems = seatHoldItemRepository.findBySeatHoldId(hold.id());
        assertEquals(
                holdItems.stream().map(SeatHoldItem::getSeatId).sorted().toList(),
                items.stream().map(BookingItem::getSeatId).sorted().toList(),
                "booking items must cover exactly the held seats");
    }

    @Test
    void amountsMatchRegardlessOfBigDecimalScale() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        // 50.0 and 50.00 are equal numerically but not by BigDecimal.equals — the service must
        // use compareTo, or a correct payment would be rejected and refunded for no reason.
        ConfirmationResult result = confirmationService.confirmFromPayment(
                UUID.randomUUID(), hold.id(), new BigDecimal("50.0"), "EUR");

        assertTrue(result.isConfirmed());
        assertEquals(1, bookingRepository.count());
    }

    // ---------- idempotency ----------

    @Test
    void redeliveredPaymentReturnsTheSameBookingInsteadOfCreatingASecond() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));
        UUID paymentId = UUID.randomUUID();

        Booking first = confirmationService
                .confirmFromPayment(paymentId, hold.id(), new BigDecimal("50.00"), "EUR").booking();
        Booking second = confirmationService
                .confirmFromPayment(paymentId, hold.id(), new BigDecimal("50.00"), "EUR").booking();

        assertEquals(first.getId(), second.getId());
        assertEquals(1, bookingRepository.count(),
                "UNIQUE(source_hold_id) backs this up at the database level");
        assertEquals(1, bookingItemRepository.count());

        // A redelivery must not announce the booking twice — a notification service consuming
        // BookingConfirmed would email the customer again.
        assertEquals(1, countOutboxEvents("BookingConfirmed"));
    }

    // ---------- failures that must trigger a refund ----------

    @Test
    void confirmationIsRejectedWhenTheAmountDoesNotMatchTheHoldSnapshot() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        ConfirmationResult result = confirmationService.confirmFromPayment(
                UUID.randomUUID(), hold.id(), new BigDecimal("10.00"), "EUR");

        assertFalse(result.isConfirmed());
        assertNotNull(result.failureReason());
        assertEquals(0, bookingRepository.count());
        assertEquals(SeatStatus.HELD, seatRepository.findById(a1.getId()).orElseThrow().getStatus(),
                "a rejected confirmation must not book the seats");

        assertEquals(1, countOutboxEvents("BookingConfirmationFailed"));
    }

    @Test
    void confirmationIsRejectedWhenTheCurrencyDoesNotMatch() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        ConfirmationResult result = confirmationService.confirmFromPayment(
                UUID.randomUUID(), hold.id(), new BigDecimal("50.00"), "USD");

        assertFalse(result.isConfirmed());
        assertEquals(0, bookingRepository.count());
        assertEquals(1, countOutboxEvents("BookingConfirmationFailed"));
    }

    @Test
    void confirmationIsRejectedWhenTheHoldWasNeverReservedForPayment() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        // deliberately no reserveForPayment — the hold is still ACTIVE

        ConfirmationResult result = confirmationService.confirmFromPayment(
                UUID.randomUUID(), hold.id(), new BigDecimal("50.00"), "EUR");

        assertFalse(result.isConfirmed());
        assertEquals(0, bookingRepository.count());
        assertEquals(1, countOutboxEvents("BookingConfirmationFailed"));
    }

    @Test
    void confirmationIsRejectedWhenTheHoldAlreadyExpired() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));

        // The race Phase 0 describes: payment succeeds moments after the hold's deadline passed.
        // Confirmation must fail and publish the event that triggers the compensating refund —
        // the money was genuinely taken.
        confirmationService.reserveForPayment(hold.id(), Instant.now().minusSeconds(30));
        seatHoldRepository.findById(hold.id()).ifPresent(h -> {
            h.expire(Instant.now());
            seatHoldRepository.saveAndFlush(h);
        });

        ConfirmationResult result = confirmationService.confirmFromPayment(
                UUID.randomUUID(), hold.id(), new BigDecimal("50.00"), "EUR");

        assertFalse(result.isConfirmed());
        assertEquals(0, bookingRepository.count());
        assertEquals(1, countOutboxEvents("BookingConfirmationFailed"));
    }

    @Test
    void aFailedConfirmationNamesThePaymentThatNeedsRefunding() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        UUID paymentId = UUID.randomUUID();
        // hold left ACTIVE — never reserved for payment

        confirmationService.confirmFromPayment(paymentId, hold.id(), new BigDecimal("50.00"), "EUR");

        OutboxEntry event = outboxRepository.findAll().stream()
                .filter(entry -> entry.getEventType().equals("BookingConfirmationFailed"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no BookingConfirmationFailed was published"));

        JsonNode payload = jsonMapper.readTree(event.getPayload()).get("payload");

        // Without the payment id, Payment Service receives the failure and has nothing to
        // reverse — the customer stays charged.
        assertEquals(paymentId.toString(), payload.get("paymentId").asString());
        assertEquals(hold.id().toString(), payload.get("holdId").asString());
        assertFalse(payload.get("reason").asString().isBlank(),
                "the reason ends up in operational logs and support tickets");
    }

    // ---------- reserve-for-payment ----------

    @Test
    void reservingForPaymentTwiceIsIdempotent() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));

        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));
        assertDoesNotThrow(() ->
                confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300)));

        assertEquals(SeatHoldStatus.PAYMENT_PENDING,
                seatHoldRepository.findById(hold.id()).orElseThrow().getStatus());
    }

    @Test
    void confirmingAnUnknownHoldFails() {
        // Still throws rather than publishing a failure event: a hold that does not exist means
        // the message references something impossible, so it should retry and dead-letter for a
        // human to look at rather than silently triggering a refund.
        assertThrows(SeatHoldNotFoundException.class,
                () -> confirmationService.confirmFromPayment(
                        UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), "EUR"));
    }

    // ---------- fixtures ----------

    private Seat createSeat(String label, String price) {
        return seatRepository.saveAndFlush(new Seat(eventId, label, new BigDecimal(price), "EUR"));
    }

    private SeatHoldResponse createHold(List<Seat> seats) {
        return seatHoldService.create(
                new CreateSeatHoldRequest(eventId, seats.stream().map(Seat::getId).toList()),
                userId,
                UUID.randomUUID().toString());
    }

    private long countOutboxEvents(String eventType) {
        return outboxRepository.findAll().stream()
                .filter(entry -> entry.getEventType().equals(eventType))
                .count();
    }
}