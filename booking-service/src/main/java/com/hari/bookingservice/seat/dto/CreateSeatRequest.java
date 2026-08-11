package com.hari.bookingservice.seat.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSeatRequest(
        @NotNull UUID eventId,
        @NotBlank String seatLabel,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotBlank String currency
) {}
