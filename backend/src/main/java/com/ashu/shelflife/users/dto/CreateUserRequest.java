package com.ashu.shelflife.users.dto;

import com.ashu.shelflife.users.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create a HUB_PICKER or CENTRAL_ADMIN account.
 *
 * @param role   role name; must be an existing role (CENTRAL_ADMIN or HUB_PICKER)
 * @param status optional; defaults to ACTIVE when omitted
 */
public record CreateUserRequest(
        @NotBlank(message = "must not be blank") @Size(max = 150) String name,
        @NotBlank(message = "must not be blank") @Email(message = "must be a valid email") String email,
        @NotBlank(message = "must not be blank") @Size(min = 8, message = "must be at least 8 characters") String password,
        @NotBlank(message = "must not be blank") String role,
        UserStatus status) {
}
