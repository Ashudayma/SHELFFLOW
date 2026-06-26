package com.ashu.shelflife.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByUserId(Long userId);
}
