package com.ashu.shelflife.users;

/**
 * Account status. Mirrors the CHECK constraint on users.status.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
