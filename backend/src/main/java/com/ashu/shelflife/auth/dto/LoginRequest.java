package com.ashu.shelflife.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login credentials. {@code usernameOrEmail} is matched against the user's email first,
 * then their name (username).
 */
public record LoginRequest(
        @NotBlank(message = "must not be blank") String usernameOrEmail,
        @NotBlank(message = "must not be blank") String password) {
}
