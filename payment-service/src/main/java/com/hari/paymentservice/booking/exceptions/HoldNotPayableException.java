package com.hari.paymentservice.booking.exceptions;

import java.util.UUID;

public class HoldNotPayableException extends RuntimeException {
    public HoldNotPayableException(UUID holdId, String reason) {
        super("Hold " + holdId + " cannot be paid for: " + reason);
    }
}