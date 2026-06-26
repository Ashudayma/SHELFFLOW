package com.ashu.shelflife.picking;

/**
 * Pick-session lifecycle. Mirrors the CHECK constraint on pick_sessions.status.
 */
public enum PickSessionStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
