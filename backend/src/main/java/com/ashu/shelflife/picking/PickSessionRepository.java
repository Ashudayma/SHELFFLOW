package com.ashu.shelflife.picking;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickSessionRepository extends JpaRepository<PickSession, Long> {

    Optional<PickSession> findByOrderIdAndStatus(Long orderId, PickSessionStatus status);
}
