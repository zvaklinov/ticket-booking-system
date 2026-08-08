package com.hari.eventservice.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidEventTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(InvalidEventTransitionException ex) {
        ApiError error = new ApiError(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
        ApiError error = new ApiError(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        ApiError error = new ApiError(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ApiError error = new ApiError(
                HttpStatus.CONFLICT.value(), "Conflict", "The request conflicts with existing data");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        ApiError error = new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "Something went wrong");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
