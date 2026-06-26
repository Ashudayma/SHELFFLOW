package com.ashu.shelflife.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An immutable audit record (BRD §4). Maps {@code audit_logs}. {@code orderId} stores the
 * internal order id; {@code action} stores the {@link AuditAction} name.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "location", length = 64)
    private String location;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;
}
