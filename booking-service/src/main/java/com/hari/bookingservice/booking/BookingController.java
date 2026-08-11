package com.hari.bookingservice.booking;

import com.hari.bookingservice.booking.dto.BookingResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getById(@PathVariable UUID bookingId) {
        return bookingService.getById(bookingId);
    }

    @GetMapping
    // TODO Phase 3: userId comes from the JWT subject; this endpoint currently lets any caller
    // read any user's bookings, which is only acceptable because no auth exists yet.
    public List<BookingResponse> listByUser(@RequestParam UUID userId) {
        return bookingService.listByUser(userId);
    }
}