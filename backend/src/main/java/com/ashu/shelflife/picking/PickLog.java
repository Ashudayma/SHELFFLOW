package com.ashu.shelflife.picking;

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
 * An immutable record of a single scan/pick (BRD 3.3). {@code scanId} is the optional
 * client idempotency key (unique; see V5). Maps {@code pick_logs}.
 */
@Entity
@Table(name = "pick_logs")
@Getter
@Setter
@NoArgsConstructor
public class PickLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "location", length = 64)
    private String location;

    // DB default now(); never written by the app.
    @Column(name = "scan_time", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime scanTime;

    @Column(name = "scan_id", length = 64, unique = true)
    private String scanId;
}
