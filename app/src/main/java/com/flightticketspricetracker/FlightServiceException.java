package com.flightticketspricetracker;

public final class FlightServiceException extends Exception {
    public final boolean retryable;

    public FlightServiceException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public FlightServiceException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }
}
