package com.ashu.shelflife.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Remove all refresh tokens for a user (used when deleting the user).
     */
    void deleteByUserId(Long userId);
}
