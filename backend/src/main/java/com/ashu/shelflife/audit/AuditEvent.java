package com.ashu.shelflife.audit;

import java.time.OffsetDateTime;

/**
 * A domain event describing something auditable that happened. Published by services via
 * {@code ApplicationEventPublisher}; written to {@code audit_logs} by an async listener.
 *
 * <p>Carries exactly the BRD §4 fields: timestamp ({@code occurredAt}), Order_ID, Item_SKU,
 * Location, User_ID — plus the {@link AuditAction} type. {@code occurredAt} is captured when
 * the action happens, so the recorded timestamp reflects the action, not the async write.
 */
public record AuditEvent(
        AuditAction action,
        Long userId,
        Long orderId,
        String sku,
        String location,
        OffsetDateTime occurredAt) {

    /** Actor-only event (e.g. login/logout). */
    public static AuditEvent of(AuditAction action, Long userId) {
        return new AuditEvent(action, userId, null, null, null, OffsetDateTime.now());
    }

    /** Order/item-scoped event (e.g. scan/skip/completion). */
    public static AuditEvent of(AuditAction action, Long userId, Long orderId,
                                String sku, String location) {
        return new AuditEvent(action, userId, orderId, sku, location, OffsetDateTime.now());
    }
}
