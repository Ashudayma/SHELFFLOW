package com.ashu.shelflife.common.error;

import java.time.OffsetDateTime;

/**
 * Structured error body returned for every handled error (401/403/4xx/5xx).
 *
 * @param timestamp ISO-8601 time the error was produced
 * @param status    HTTP status code
 * @param error     HTTP reason phrase (e.g. "Unauthorized", "Forbidden")
 * @param message   human-readable detail
 * @param path      request URI that produced the error
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path);
    }
}
