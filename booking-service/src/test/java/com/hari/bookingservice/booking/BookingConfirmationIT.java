package com.hari.bookingservice.booking;

import com.hari.bookingservice.TestcontainersConfiguration;
import com.hari.bookingservice.booking.exceptions.BookingConfirmationFailedException;
import com.hari.bookingservice.event.EventBookability;
import com.hari.bookingservice.event.EventBookabilityRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the PAYMENT_PENDING -> CONFIRMED transition. In Phase 5 this will be driven by the
 * PaymentSucceeded Kafka consumer; the consumer will deserialize the message and call exactly the
 * method these tests call, so the transition logic is proven before Payment Service exists.
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
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID eventId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE seat_hold_item, booking_item, booking, seat_hold, seat, "
                + "event_bookability, idempotency_key CASCADE");

        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        eventBookabilityRepository.save(new EventBookability(
                eventId, "PUBLISHED",
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.plus(31, ChronoUnit.DAYS)));
    }

    @Test
    void confirmingAPaidHoldCreatesABookingAndBooksTheSeats() {
        Seat a1 = createSeat("A1", "50.00");
        Seat a2 = createSeat("A2", "70.00");
        SeatHoldResponse hold = createHold(List.of(a1, a2));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        Booking booking = confirmationService.confirmFromPayment(
                hold.id(), new BigDecimal("120.00"), "EUR");

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
    }

    @Test
    void bookingItemsCarryTheHoldsSnapshottedPricesNotLiveSeatPrices() {
        Seat a1 = createSeat("A1", "50.00");
        Seat a2 = createSeat("A2", "70.00");
        SeatHoldResponse hold = createHold(List.of(a1, a2));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        Booking booking = confirmationService.confirmFromPayment(
                hold.id(), new BigDecimal("120.00"), "EUR");

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
    void redeliveredPaymentReturnsTheSameBookingInsteadOfCreatingASecond() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        Booking first = confirmationService.confirmFromPayment(hold.id(), new BigDecimal("50.00"), "EUR");
        Booking second = confirmationService.confirmFromPayment(hold.id(), new BigDecimal("50.00"), "EUR");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, bookingRepository.count(), "UNIQUE(source_hold_id) backs this up at the DB level");
        assertEquals(1, bookingItemRepository.count());
    }

    @Test
    void confirmationIsRejectedWhenTheAmountDoesNotMatchTheHoldSnapshot() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        assertThrows(BookingConfirmationFailedException.class,
                () -> confirmationService.confirmFromPayment(hold.id(), new BigDecimal("10.00"), "EUR"));

        assertEquals(0, bookingRepository.count());
        assertEquals(SeatStatus.HELD, seatRepository.findById(a1.getId()).orElseThrow().getStatus(),
                "a rejected confirmation must not book the seats");
    }

    @Test
    void confirmationIsRejectedWhenTheCurrencyDoesNotMatch() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        assertThrows(BookingConfirmationFailedException.class,
                () -> confirmationService.confirmFromPayment(hold.id(), new BigDecimal("50.00"), "USD"));

        assertEquals(0, bookingRepository.count());
    }

    @Test
    void amountsMatchRegardlessOfBigDecimalScale() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        confirmationService.reserveForPayment(hold.id(), Instant.now().plusSeconds(300));

        // 50.0 and 50.00 are equal numerically but not by BigDecimal.equals — the service must
        // use compareTo, or a correct payment would be rejected.
        assertDoesNotThrow(() ->
                confirmationService.confirmFromPayment(hold.id(), new BigDecimal("50.0"), "EUR"));

        assertEquals(1, bookingRepository.count());
    }

    @Test
    void confirmationIsRejectedWhenTheHoldWasNeverReservedForPayment() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));
        // deliberately no reserveForPayment — the hold is still ACTIVE

        assertThrows(BookingConfirmationFailedException.class,
                () -> confirmationService.confirmFromPayment(hold.id(), new BigDecimal("50.00"), "EUR"));

        assertEquals(0, bookingRepository.count());
    }

    @Test
    void confirmationIsRejectedWhenTheHoldAlreadyExpired() {
        Seat a1 = createSeat("A1", "50.00");
        SeatHoldResponse hold = createHold(List.of(a1));

        // The race Phase 0 describes: payment succeeds moments after the hold's deadline passed.
        // Confirmation must fail here so that Phase 5 can publish BookingConfirmationFailed and
        // trigger the compensating refund — the money was genuinely taken.
        confirmationService.reserveForPayment(hold.id(), Instant.now().minusSeconds(30));
        seatHoldRepository.findById(hold.id()).ifPresent(h -> {
            h.expire(Instant.now());
            seatHoldRepository.saveAndFlush(h);
        });

        assertThrows(BookingConfirmationFailedException.class,
                () -> confirmationService.confirmFromPayment(hold.id(), new BigDecimal("50.00"), "EUR"));

        assertEquals(0, bookingRepository.count());
    }

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
        assertThrows(SeatHoldNotFoundException.class,
                () -> confirmationService.confirmFromPayment(
                        UUID.randomUUID(), new BigDecimal("50.00"), "EUR"));
    }

    // ---------- fixtures ----------

    private Seat createSeat(String label, String price) {
        return seatRepository.saveAndFlush(new Seat(eventId, label, new BigDecimal(price), "EUR"));
    }

    private SeatHoldResponse createHold(List<Seat> seats) {
        return seatHoldService.create(
                new CreateSeatHoldRequest(eventId, userId, seats.stream().map(Seat::getId).toList()),
                UUID.randomUUID().toString());
    }
}