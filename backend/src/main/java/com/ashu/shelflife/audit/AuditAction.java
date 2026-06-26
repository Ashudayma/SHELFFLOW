package com.ashu.shelflife.audit;

/**
 * The auditable actions (BRD §4 Audit Trail).
 */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    SCAN,
    SKIP,
    ITEM_PICKED,
    ORDER_COMPLETED,
    DOWNLOAD_REPORT
}
