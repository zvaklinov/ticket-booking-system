package com.hari.identityservice.common;

import java.time.Instant;

public class ApiError {
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String message;

    public ApiError(int status, String error, String message) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public Instant getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
}
