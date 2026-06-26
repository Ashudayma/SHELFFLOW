package com.ashu.shelflife.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts exceptions raised during request handling into the structured {@link ApiError}
 * body. Authentication failures surface as 401 and authorization failures (including
 * {@code @PreAuthorize} denials raised during method invocation) as 403, matching the
 * filter-level handlers so every error path returns the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Failed login / invalid or expired refresh token, etc. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /** {@code @PreAuthorize} / authorization denial raised during method invocation. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN,
                "Access denied: your role does not permit this operation.", request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "The request conflicts with existing data or referential constraints.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Validation failed.");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest request) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(),
                message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
