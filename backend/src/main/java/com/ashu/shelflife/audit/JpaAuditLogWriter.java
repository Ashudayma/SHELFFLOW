package com.ashu.shelflife.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an audit row in its own transaction (REQUIRES_NEW so the audit commit is fully
 * independent of any caller transaction).
 */
@Component
public class JpaAuditLogWriter implements AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    public JpaAuditLogWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent event) {
        AuditLog log = new AuditLog();
        log.setUserId(event.userId());
        log.setAction(event.action().name());
        log.setOrderId(event.orderId());
        log.setSku(event.sku());
        log.setLocation(event.location());
        log.setTimestamp(event.occurredAt());
        auditLogRepository.save(log);
    }
}
