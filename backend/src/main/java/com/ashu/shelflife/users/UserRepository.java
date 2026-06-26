package com.ashu.shelflife.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Resolve a login identifier that may be either the user's email or their name (username).
     */
    Optional<User> findByEmailOrName(String email, String name);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
}
