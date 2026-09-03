// com/hari/paymentservice/common/GlobalExceptionHandler.java
package com.hari.paymentservice.common;

import com.hari.paymentservice.booking.exceptions.BookingServiceUnavailableException;
import com.hari.paymentservice.booking.exceptions.HoldNotPayableException;
import com.hari.paymentservice.booking.exceptions.SeatHoldNotFoundException;
import com.hari.paymentservice.payment.exceptions.InvalidPaymentTransitionException;
import com.hari.paymentservice.payment.exceptions.PaymentAccessDeniedException;
import com.hari.paymentservice.payment.exceptions.PaymentNotFoundException;
import com.hari.paymentservice.payment.exceptions.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidPaymentTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidPaymentTransition(InvalidPaymentTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Deliberately generic. The specific constraint name would leak schema details, and the
        // useful diagnosis is in the log rather than the response.
        log.warn("Constraint violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", "The request conflicts with existing data."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, "Bad Request", message));
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiError> handleInvalidQuery(InvalidDataAccessApiUsageException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, "Bad Request",
                        "Invalid query parameters — check sort fields and filters."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {

        log.error("Unhandled exception", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, "Internal Server Error", "Something went wrong"));
    }

    @ExceptionHandler(SeatHoldNotFoundException.class)
    public ResponseEntity<ApiError> handleSeatHoldNotFound(SeatHoldNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(HoldNotPayableException.class)
    public ResponseEntity<ApiError> handleHoldNotPayable(HoldNotPayableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(BookingServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleBookingServiceUnavailable(BookingServiceUnavailableException ex) {
        log.error("Booking Service unreachable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(503, "Service Unavailable",
                        "Unable to validate the hold with Booking Service. Please retry."));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(PaymentAccessDeniedException.class)
    public ResponseEntity<ApiError> handlePaymentAccessDenied(PaymentAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(403, "Forbidden", ex.getMessage()));
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ApiError> handlePaymentProvider(PaymentProviderException ex) {
        log.error("Payment provider failure", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError(502, "Bad Gateway",
                        "The payment provider could not be reached. Please retry."));
    }
}