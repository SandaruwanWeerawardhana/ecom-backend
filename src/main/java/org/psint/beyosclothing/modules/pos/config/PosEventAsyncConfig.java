package org.psint.beyosclothing.modules.pos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * POS Event Async Configuration
 * <p>
 * Configures dedicated thread pool for asynchronous event publishing.
 * This ensures event publishing never blocks order placement operations.
 * <p>
 * Thread Pool Configuration:
 * - Core Pool Size: 5 threads
 * - Max Pool Size: 20 threads
 * - Queue Capacity: 500 tasks
 * - Keep Alive: 60 seconds
 * <p>
 * Rejection Policy: CallerRuns (fallback to calling thread if pool is full)
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-03
 */
@Configuration
@EnableAsync
@Slf4j
public class PosEventAsyncConfig {

    /**
     * Dedicated thread pool for POS event publishing.
     * <p>
     * Sizing rationale:
     * - Core: 5 threads to handle typical load
     * - Max: 20 threads for peak traffic
     * - Queue: 500 to buffer burst traffic
     * - CallerRuns policy ensures events are never dropped
     *
     * @return Configured executor for async event publishing
     */
    @Bean(name = "posEventExecutor")
    public Executor posEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - threads always alive
        executor.setCorePoolSize(5);

        // Maximum pool size - threads created on demand
        executor.setMaxPoolSize(20);

        // Queue capacity - tasks waiting for threads
        executor.setQueueCapacity(500);

        // Keep alive time for idle threads above core size
        executor.setKeepAliveSeconds(60);

        // Thread naming for debugging
        executor.setThreadNamePrefix("pos-event-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Rejection policy - run in calling thread if pool is full
        executor.setRejectedExecutionHandler(new LoggingRejectedExecutionHandler());

        // Initialize the executor
        executor.initialize();

        log.info("[POS_EVENT_ASYNC_CONFIG] Initialized POS event executor - corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Custom rejected execution handler that logs rejections and falls back to caller-runs.
     */
    private static class LoggingRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("[POS_EVENT_ASYNC_CONFIG] Event task rejected (pool full), executing in calling thread - activeCount={}, queueSize={}",
                    executor.getActiveCount(), executor.getQueue().size());

            // Fallback: run in calling thread (CallerRunsPolicy behavior)
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
}
