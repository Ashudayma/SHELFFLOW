package com.ashu.shelflife.common.error;

/**
 * Thrown when a request conflicts with current state (e.g. a duplicate unique key).
 * Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
