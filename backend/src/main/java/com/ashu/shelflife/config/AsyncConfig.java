package com.ashu.shelflife.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and provides the dedicated executor for audit-log writes, so the
 * audit trail never runs on (or blocks) request-handling threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("audit-");
        executor.initialize();
        return executor;
    }
}
