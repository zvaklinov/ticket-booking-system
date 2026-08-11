package com.hari.bookingservice.booking;

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
    private final Clock clock;
    private final Duration paymentDeadline;

    public InternalSeatHoldController(BookingConfirmationService bookingConfirmationService,
                                      Clock clock,
                                      @Value("${booking.hold.payment-deadline}") Duration paymentDeadline) {
        this.bookingConfirmationService = bookingConfirmationService;
        this.clock = clock;
        this.paymentDeadline = paymentDeadline;
    }

    @PostMapping("/{holdId}/reserve-for-payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveForPayment(@PathVariable UUID holdId) {
        bookingConfirmationService.reserveForPayment(holdId, clock.instant().plus(paymentDeadline));
    }
}