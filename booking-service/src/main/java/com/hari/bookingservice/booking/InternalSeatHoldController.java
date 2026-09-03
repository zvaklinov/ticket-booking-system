package com.hari.bookingservice.booking;

import com.hari.bookingservice.booking.dto.SeatHoldPaymentDetails;
import com.hari.bookingservice.seathold.SeatHold;
import com.hari.bookingservice.seathold.SeatHoldRepository;
import com.hari.bookingservice.seathold.exceptions.SeatHoldNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/internal/seat-holds")
public class InternalSeatHoldController {

    private final BookingConfirmationService bookingConfirmationService;
    private final SeatHoldRepository seatHoldRepository;
    private final Clock clock;
    private final Duration paymentDeadline;

    public InternalSeatHoldController(BookingConfirmationService bookingConfirmationService,
                                      SeatHoldRepository seatHoldRepository,
                                      Clock clock,
                                      @Value("${booking.hold.payment-deadline}") Duration paymentDeadline) {
        this.bookingConfirmationService = bookingConfirmationService;
        this.seatHoldRepository = seatHoldRepository;
        this.clock = clock;
        this.paymentDeadline = paymentDeadline;
    }

    @GetMapping("/{holdId}/payment-details")
    public SeatHoldPaymentDetails paymentDetails(@PathVariable UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new SeatHoldNotFoundException(holdId));

        return new SeatHoldPaymentDetails(
                hold.getId(), hold.getEventId(), hold.getUserId(), hold.getStatus().name(),
                hold.getTotalAmount(), hold.getCurrency(), hold.getExpiresAt());
    }

    @PostMapping("/{holdId}/reserve-for-payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveForPayment(@PathVariable UUID holdId) {
        bookingConfirmationService.reserveForPayment(holdId, clock.instant().plus(paymentDeadline));
    }
}