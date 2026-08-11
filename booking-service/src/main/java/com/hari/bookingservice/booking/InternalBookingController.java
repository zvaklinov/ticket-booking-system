package com.hari.bookingservice.booking;

import com.hari.bookingservice.booking.dto.BookingSummaryResponse;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/bookings")
public class InternalBookingController {

    private final BookingRepository bookingRepository;

    public InternalBookingController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * Called synchronously by Event Service before permitting a restricted-field edit.
     * Admin-time and low frequency — one of the few sanctioned synchronous cross-service calls.
     */
    @GetMapping("/by-event/{eventId}/summary")
    public BookingSummaryResponse summaryByEvent(@PathVariable UUID eventId) {
        return new BookingSummaryResponse(
                eventId,
                bookingRepository.countByEventIdAndStatus(eventId, BookingStatus.CONFIRMED));
    }
}