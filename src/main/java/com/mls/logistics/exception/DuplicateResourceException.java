package com.mls.logistics.exception;

/**
 * Exception thrown when a request would violate a uniqueness constraint
 * (e.g. a stock row for a resource/warehouse pair that already exists).
 *
 * Caught by GlobalExceptionHandler and converted to 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
