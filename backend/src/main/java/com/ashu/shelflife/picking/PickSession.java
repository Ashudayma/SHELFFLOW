package com.ashu.shelflife.picking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A picker's working session for one order (BRD 3.3). Opened on the first scan, closed
 * (end_time set, status COMPLETED) when the order is fully picked. Maps {@code pick_sessions}.
 */
@Entity
@Table(name = "pick_sessions")
@Getter
@Setter
@NoArgsConstructor
public class PickSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "picker_id", nullable = false)
    private Long pickerId;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PickSessionStatus status = PickSessionStatus.OPEN;
}
