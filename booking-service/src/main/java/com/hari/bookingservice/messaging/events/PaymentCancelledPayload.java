package com.hari.bookingservice.messaging.events;

import java.util.UUID;

public record PaymentCancelledPayload(UUID paymentId, UUID holdId) {}