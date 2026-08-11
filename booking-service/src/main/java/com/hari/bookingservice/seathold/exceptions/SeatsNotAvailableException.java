package com.hari.bookingservice.seathold.exceptions;

import java.util.List;

public class SeatsNotAvailableException extends RuntimeException {

    public SeatsNotAvailableException(List<String> seatLabels) {
        super("Seats not available: " + String.join(", ", seatLabels));
    }
}
