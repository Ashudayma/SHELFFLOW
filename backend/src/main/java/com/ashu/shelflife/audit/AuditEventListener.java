package com.ashu.shelflife.audit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cross-cutting audit sink (BRD §4). Listens for {@link AuditEvent}s and writes them to
 * {@code audit_logs} <strong>asynchronously</strong>, on a dedicated executor, <strong>after
 * the publishing transaction commits</strong>. This keeps the audit write entirely off the
 * request thread so performance-critical paths (e.g. {@code POST /scan}) never block on it,
 * and ensures only committed actions are audited.
 *
 * <p>{@code fallbackExecution = true} so events published outside a transaction (should there
 * be any) are still recorded.
 */
@Component
public class AuditEventListener {

    private final AuditLogWriter auditLogWriter;

    public AuditEventListener(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditEvent event) {
        auditLogWriter.write(event);
    }
}
