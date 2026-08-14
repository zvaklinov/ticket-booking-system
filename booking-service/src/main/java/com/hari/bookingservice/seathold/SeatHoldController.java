package com.hari.bookingservice.seathold;

import com.hari.bookingservice.common.AuthenticatedUser;
import com.hari.bookingservice.seathold.dto.CreateSeatHoldRequest;
import com.hari.bookingservice.seathold.dto.SeatHoldResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @AuthenticationPrincipal Jwt jwt) {
        return seatHoldService.create(request, AuthenticatedUser.idOf(jwt), idempotencyKey);
    }

    @GetMapping("/{holdId}")
    public SeatHoldResponse getById(@PathVariable UUID holdId, @AuthenticationPrincipal Jwt jwt) {
        return seatHoldService.getById(holdId, AuthenticatedUser.idOf(jwt));
    }

    @PostMapping("/{holdId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID holdId, @AuthenticationPrincipal Jwt jwt) {
        seatHoldService.release(holdId, AuthenticatedUser.idOf(jwt));
    }
}