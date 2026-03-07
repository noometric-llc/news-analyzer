package org.newsanalyzer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for background sync operations.
 *
 * Provides a dedicated thread pool ("syncExecutor") for long-running sync jobs
 * so they don't block HTTP request threads. Uses CallerRunsPolicy as a safe
 * degradation strategy — if the pool is saturated, the calling thread runs the
 * task itself (effectively falling back to synchronous behavior).
 *
 * Key Spring @Async concept: @Async methods must be called across bean boundaries
 * for the AOP proxy to intercept. Calling an @Async method from within the same
 * bean bypasses the proxy and runs synchronously.
 *
 * @since 2.0.0
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "syncExecutor")
    public Executor syncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Initialized syncExecutor thread pool: core=2, max=4, queue=10");
        return executor;
    }
}
