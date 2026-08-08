package com.hari.eventservice.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateEventRequest(
        @NotNull UUID categoryId,
        @NotBlank String title,
        @NotNull Instant startTimeUtc,
        @NotNull Instant endTimeUtc,
        @NotNull Instant bookingOpensAtUtc,
        @NotNull Instant bookingClosesAtUtc,
        @NotBlank String currency
) {
}