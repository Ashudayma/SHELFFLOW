package com.ashu.shelflife.picking;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickLogRepository extends JpaRepository<PickLog, Long> {

    Optional<PickLog> findByScanId(String scanId);
}
