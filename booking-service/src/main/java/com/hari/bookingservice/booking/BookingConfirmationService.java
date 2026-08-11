package com.hari.bookingservice.booking;

import com.hari.bookingservice.booking.exceptions.BookingConfirmationFailedException;
import com.hari.bookingservice.seat.SeatRepository;
import com.hari.bookingservice.seathold.*;
import com.hari.bookingservice.seathold.exceptions.SeatHoldNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingConfirmationService {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldItemRepository seatHoldItemRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final Clock clock;

    public BookingConfirmationService(SeatHoldRepository seatHoldRepository,
                                      SeatHoldItemRepository seatHoldItemRepository,
                                      SeatRepository seatRepository,
                                      BookingRepository bookingRepository,
                                      BookingItemRepository bookingItemRepository,
                                      Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatHoldItemRepository = seatHoldItemRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.clock = clock;
    }

    /**
     * Phase 5 will call this from the PaymentSucceeded Kafka consumer. It is a plain method now,
     * so the transition can be built and tested before Payment Service exists — the consumer will
     * deserialize the message and call this unchanged.
     */
    @Transactional
    public Booking confirmFromPayment(UUID holdId, BigDecimal paidAmount, String paidCurrency) {
        // UNIQUE(source_hold_id) is the database-level backstop behind this check.
        Booking existing = bookingRepository.findBySourceHoldId(holdId).orElse(null);
        if (existing != null) {
            return existing;
        }

        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));

        if (hold.getStatus() != SeatHoldStatus.PAYMENT_PENDING) {
            // Phase 5: publish BookingConfirmationFailed here, which triggers the automatic
            // refund. Money was taken for a hold that can no longer be honoured.
            throw new BookingConfirmationFailedException(holdId,
                    "hold is " + hold.getStatus() + ", expected PAYMENT_PENDING");
        }

        // compareTo, not equals: BigDecimal equality is scale-sensitive, so 50.0 != 50.00.
        if (hold.getTotalAmount().compareTo(paidAmount) != 0
                || !hold.getCurrency().equals(paidCurrency)) {
            throw new BookingConfirmationFailedException(holdId,
                    "payment " + paidAmount + " " + paidCurrency
                            + " does not match hold total " + hold.getTotalAmount() + " " + hold.getCurrency());
        }

        List<SeatHoldItem> holdItems = seatHoldItemRepository.findBySeatHoldId(holdId);

        // Must happen BEFORE bookSeatsForHold: that query's clearAutomatically = true detaches
        // this entity, after which dirty checking would silently ignore the change.
        // flushAutomatically = true on the query pushes this update out first.
        Instant now = clock.instant();
        hold.confirm(now);

        int seatsBooked = seatRepository.bookSeatsForHold(holdId);
        if (seatsBooked != holdItems.size()) {
            throw new BookingConfirmationFailedException(holdId,
                    "expected to book " + holdItems.size() + " seat(s) but updated " + seatsBooked);
        }

        Booking booking = bookingRepository.saveAndFlush(new Booking(
                holdId,
                hold.getEventId(),
                hold.getUserId(),
                hold.getTotalAmount(),
                hold.getCurrency(),
                now));

        // Prices are snapshotted forward from the hold items, never re-read from seats.
        bookingItemRepository.saveAll(holdItems.stream()
                .map(item -> new BookingItem(booking.getId(), item.getSeatId(),
                        item.getPrice(), item.getCurrency()))
                .toList());

        // TODO Phase 4: publish BookingConfirmed via the outbox, in this same transaction.
        return booking;
    }

    /**
     * Phase 5: called by Payment Service via POST /internal/seat-holds/{holdId}/reserve-for-payment.
     */
    @Transactional
    public void reserveForPayment(UUID holdId, Instant paymentDeadlineAt) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));
        hold.reserveForPayment(paymentDeadlineAt);
    }
}