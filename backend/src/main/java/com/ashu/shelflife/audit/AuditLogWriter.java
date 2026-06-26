package com.ashu.shelflife.audit;

/**
 * Persists an {@link AuditEvent}. Separated from the event listener so the persistence step is
 * a clean seam (and overridable in tests).
 */
public interface AuditLogWriter {

    void write(AuditEvent event);
}
