package com.hari.bookingservice.common;

import com.hari.bookingservice.booking.exceptions.BookingConfirmationFailedException;
import com.hari.bookingservice.booking.exceptions.BookingNotFoundException;
import com.hari.bookingservice.booking.exceptions.CancellationDeadlinePassedException;
import com.hari.bookingservice.booking.exceptions.InvalidBookingTransitionException;
import com.hari.bookingservice.event.EventNotFoundException;
import com.hari.bookingservice.event.EventServiceUnavailableException;
import com.hari.bookingservice.seat.exceptions.InvalidSeatTransitionException;
import com.hari.bookingservice.seat.exceptions.SeatCreationNotAllowedException;
import com.hari.bookingservice.seat.exceptions.SeatNotFoundException;
import com.hari.bookingservice.seathold.exceptions.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> handleEventNotFound(EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(EventServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleEventServiceUnavailable(EventServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(503, "Service Unavailable",
                        "Unable to validate the event with Event Service. Please retry."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict",
                        "The request conflicts with existing data."));
    }

    @ExceptionHandler(SeatCreationNotAllowedException.class)
    public ResponseEntity<ApiError> handleSeatCreationNotAllowed(SeatCreationNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSeatTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidSeatTransition(InvalidSeatTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(SeatsNotAvailableException.class)
    public ResponseEntity<ApiError> handleSeatsNotAvailable(SeatsNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(EventNotBookableException.class)
    public ResponseEntity<ApiError> handleEventNotBookable(EventNotBookableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(ActiveHoldAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleActiveHoldExists(ActiveHoldAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(SeatHoldNotFoundException.class)
    public ResponseEntity<ApiError> handleSeatHoldNotFound(SeatHoldNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(SeatHoldAccessDeniedException.class)
    public ResponseEntity<ApiError> handleSeatHoldAccessDenied(SeatHoldAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(403, "Forbidden", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, "Bad Request", message));
    }

    @ExceptionHandler(BookingConfirmationFailedException.class)
    public ResponseEntity<ApiError> handleBookingConfirmationFailed(BookingConfirmationFailedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler({InvalidBookingTransitionException.class, InvalidSeatHoldTransitionException.class})
    public ResponseEntity<ApiError> handleInvalidTransition(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(CancellationDeadlinePassedException.class)
    public ResponseEntity<ApiError> handleCancellationDeadline(CancellationDeadlinePassedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler({BookingNotFoundException.class, SeatNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, "Not Found", ex.getMessage()));
    }
}
