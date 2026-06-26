package com.ashu.shelflife.users.dto;

import com.ashu.shelflife.users.User;
import com.ashu.shelflife.users.UserStatus;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        UserStatus status) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getStatus());
    }
}
