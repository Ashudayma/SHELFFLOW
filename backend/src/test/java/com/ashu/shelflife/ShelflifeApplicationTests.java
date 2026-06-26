package com.ashu.shelflife;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ShelflifeApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts, the datasource connects,
        // and Flyway migrations apply cleanly against the configured database.
    }
}
