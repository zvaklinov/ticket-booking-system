package com.hari.bookingservice.seathold.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateSeatHoldRequest(
        @NotNull UUID eventId,
        @NotEmpty @Size(min = 1, max = 6) List<UUID> seatIds
) {}
