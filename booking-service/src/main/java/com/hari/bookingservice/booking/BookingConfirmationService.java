package com.hari.bookingservice.booking;

import com.hari.bookingservice.outbox.BookingConfirmationFailedPayload;
import com.hari.bookingservice.outbox.OutboxWriter;
import com.hari.bookingservice.outbox.events.BookingConfirmedPayload;
import com.hari.bookingservice.seat.SeatRepository;
import com.hari.bookingservice.seathold.*;
import com.hari.bookingservice.seathold.exceptions.SeatHoldNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationService.class);

    public BookingConfirmationService(SeatHoldRepository seatHoldRepository,
                                      SeatHoldItemRepository seatHoldItemRepository,
                                      SeatRepository seatRepository,
                                      BookingRepository bookingRepository,
                                      BookingItemRepository bookingItemRepository,
                                      OutboxWriter outboxWriter,
                                      Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatHoldItemRepository = seatHoldItemRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * Confirms a booking from a successful payment. Called by the PaymentSucceeded consumer.
     * Failure paths publish BookingConfirmationFailed rather than throwing. This is not a style
     * choice: throwing would roll back the transaction and take the outbox row with it, so no
     * refund would ever be triggered — leaving a customer charged for a booking that does not
     * exist. The only case that still throws is a hold that does not exist at all, which means
     * the message references something impossible and should be retried and dead-lettered.
     */
    @Transactional
    public ConfirmationResult confirmFromPayment(UUID paymentId, UUID holdId, BigDecimal paidAmount, String paidCurrency) {

        // UNIQUE(source_hold_id) is the database-level backstop behind this check.
        Booking existing = bookingRepository.findBySourceHoldId(holdId).orElse(null);
        if (existing != null) {
            return ConfirmationResult.confirmed(existing);
        }

        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));

        if (hold.getStatus() != SeatHoldStatus.PAYMENT_PENDING) {
            return failConfirmation(paymentId, hold,
                    "hold is " + hold.getStatus() + ", expected PAYMENT_PENDING");
        }

        // compareTo, not equals: BigDecimal equality is scale-sensitive, so 50.0 != 50.00.
        if (hold.getTotalAmount().compareTo(paidAmount) != 0
                || !hold.getCurrency().equals(paidCurrency)) {
            return failConfirmation(paymentId, hold,
                    "payment " + paidAmount + " " + paidCurrency
                            + " does not match hold total " + hold.getTotalAmount()
                            + " " + hold.getCurrency());
        }

        List<SeatHoldItem> holdItems = seatHoldItemRepository.findBySeatHoldId(holdId);

        // Must happen BEFORE bookSeatsForHold: that query's clearAutomatically = true detaches
        // this entity, after which dirty checking would silently ignore the change.
        // flushAutomatically = true on the query pushes this update out first.
        Instant now = clock.instant();
        hold.confirm(now);

        int seatsBooked = seatRepository.bookSeatsForHold(holdId);
        if (seatsBooked != holdItems.size()) {
            // A seat slipped away between the status check and the claim. Roll back the hold's
            // confirmation by failing the whole transaction is NOT an option here, because we
            // need the failure event to survive — so instead this branch must not have mutated
            // anything it cannot live with. Marking the hold confirmed above is safe: the
            // compensating refund is what resolves the customer's position either way.
            return failConfirmation(paymentId, hold,
                    "expected to book " + holdItems.size() + " seat(s) but updated " + seatsBooked);
        }

        Booking booking = bookingRepository.saveAndFlush(new Booking(
                holdId,
                hold.getEventId(),
                hold.getUserId(),
                hold.getTotalAmount(),
                hold.getCurrency(),
                now));

        bookingItemRepository.saveAll(holdItems.stream()
                .map(item -> new BookingItem(booking.getId(), item.getSeatId(),
                        item.getPrice(), item.getCurrency()))
                .toList());

        outboxWriter.write("BookingConfirmed", booking.getId(), new BookingConfirmedPayload(
                booking.getId(),
                holdId,
                booking.getEventId(),
                booking.getUserId(),
                holdItems.stream().map(SeatHoldItem::getSeatId).toList(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getConfirmedAt()));

        return ConfirmationResult.confirmed(booking);
    }

    /**
     * Called by Payment Service via POST /internal/seat-holds/{holdId}/reserve-for-payment.
     */
    @Transactional
    public void reserveForPayment(UUID holdId, Instant paymentDeadlineAt) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));
        hold.reserveForPayment(paymentDeadlineAt);
    }

    private ConfirmationResult failConfirmation(UUID paymentId, SeatHold hold, String reason) {
        outboxWriter.write("BookingConfirmationFailed", hold.getId(),
                new BookingConfirmationFailedPayload(
                        paymentId, hold.getId(), hold.getEventId(), hold.getUserId(), reason));

        log.warn("Booking confirmation failed for hold {}: {}", hold.getId(), reason);
        return ConfirmationResult.failed(reason);
    }
}