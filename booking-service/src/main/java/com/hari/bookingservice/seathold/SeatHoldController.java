package com.hari.bookingservice.seathold;

import com.hari.bookingservice.seathold.dto.CreateSeatHoldRequest;
import com.hari.bookingservice.seathold.dto.SeatHoldResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/seat-holds")
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatHoldResponse create(@Valid @RequestBody CreateSeatHoldRequest request,
                                   @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return seatHoldService.create(request, idempotencyKey);
    }

    @GetMapping("/{holdId}")
    public SeatHoldResponse getById(@PathVariable UUID holdId) {
        return seatHoldService.getById(holdId);
    }

    @PostMapping("/{holdId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID holdId,
                        // TODO Phase 3: replace with the JWT subject.
                        @RequestParam UUID userId) {
        seatHoldService.release(holdId, userId);
    }
}