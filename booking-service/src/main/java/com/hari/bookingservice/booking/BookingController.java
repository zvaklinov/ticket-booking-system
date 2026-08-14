package com.hari.bookingservice.booking;

import com.hari.bookingservice.booking.dto.BookingResponse;
import com.hari.bookingservice.common.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public BookingResponse getById(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getById(bookingId, AuthenticatedUser.idOf(jwt));
    }

    @GetMapping
    public List<BookingResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.listByUser(AuthenticatedUser.idOf(jwt));
    }
}