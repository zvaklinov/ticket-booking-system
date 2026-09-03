package com.hari.paymentservice.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePaymentRequest(@NotNull UUID holdId) {}