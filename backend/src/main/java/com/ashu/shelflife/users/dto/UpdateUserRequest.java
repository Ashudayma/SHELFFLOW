package com.ashu.shelflife.users.dto;

import com.ashu.shelflife.users.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Full update of an account. {@code password} is optional — supply it only to reset it.
 */
public record UpdateUserRequest(
        @NotBlank(message = "must not be blank") @Size(max = 150) String name,
        @NotBlank(message = "must not be blank") @Email(message = "must be a valid email") String email,
        @NotBlank(message = "must not be blank") String role,
        @NotNull(message = "must not be null") UserStatus status,
        @Size(min = 8, message = "must be at least 8 characters") String password) {
}
