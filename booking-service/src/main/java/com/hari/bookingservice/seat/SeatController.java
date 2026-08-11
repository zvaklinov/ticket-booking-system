package com.hari.bookingservice.seat;

import com.hari.bookingservice.seat.dto.CreateSeatRequest;
import com.hari.bookingservice.seat.dto.SeatResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping
    public List<SeatResponse> listByEvent(@RequestParam UUID eventId) {
        return seatService.listByEvent(eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse create(@Valid @RequestBody CreateSeatRequest request) {
        return seatService.create(request);
    }

    @PostMapping("/{seatId}/unavailable")
    public SeatResponse markUnavailable(@PathVariable UUID seatId) {
        return seatService.markUnavailable(seatId);
    }

    @PostMapping("/{seatId}/available")
    public SeatResponse markAvailable(@PathVariable UUID seatId) {
        return seatService.markAvailable(seatId);
    }
}
