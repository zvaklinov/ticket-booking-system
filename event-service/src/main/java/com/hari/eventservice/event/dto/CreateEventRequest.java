package com.hari.eventservice.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank String title,
        String description,
        @NotNull UUID categoryId,
        String location,
        String venueName,
        String venueTimezone,
        @NotNull Instant startTimeUtc,
        @NotNull Instant endTimeUtc,
        @NotNull Instant bookingOpensAtUtc,
        @NotNull Instant bookingClosesAtUtc,
        @NotBlank String currency
) {}