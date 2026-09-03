package com.hari.bookingservice.booking;

/**
 * Confirmation either produces a booking or a documented failure. Returning null for the
 * failure case invites a NullPointerException; making both outcomes explicit
 * forces the caller to handle them.
 */
public record ConfirmationResult(Booking booking, String failureReason) {

    public static ConfirmationResult confirmed(Booking booking) {
        return new ConfirmationResult(booking, null);
    }

    public static ConfirmationResult failed(String reason) {
        return new ConfirmationResult(null, reason);
    }

    public boolean isConfirmed() {
        return booking != null;
    }
}