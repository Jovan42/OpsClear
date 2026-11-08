package com.opsclear.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async infrastructure for notification fan-out (ADR-0051). {@code notificationExecutor} is a dedicated, bounded
 * pool: when it's saturated the event is logged and dropped, because notifications are best-effort and the action
 * that triggered them has already committed.
 *
 * <p>The bean is not a default autowire candidate, so Spring Boot's own {@code applicationTaskExecutor} (used by
 * Spring MVC async requests) is still auto-configured. {@code @Async} finds it by name.
 *
 * <p>{@code notifications.synchronous=true} (test profile) swaps in a {@link SyncTaskExecutor}, so fan-out finishes
 * before the triggering request returns and integration tests don't race their own cleanup.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    static final int CORE_POOL_SIZE = 2;
    static final int MAX_POOL_SIZE = 4;
    static final int QUEUE_CAPACITY = 1_000;
    static final int SHUTDOWN_AWAIT_SECONDS = 10;
    static final String THREAD_NAME_PREFIX = "notification-";

    @Bean(name = NOTIFICATION_EXECUTOR, defaultCandidate = false)
    public TaskExecutor notificationExecutor(@Value("${notifications.synchronous:false}") boolean synchronous) {
        if (synchronous) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(AsyncConfig::dropAndLog);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS);
        return executor;
    }

    // Rejection policy: log and drop instead of throwing back into the after-commit callback.
    static void dropAndLog(Runnable task, ThreadPoolExecutor pool) {
        log.warn("Notification executor saturated (active {}, queued {}): dropping a notification event",
                pool.getActiveCount(), pool.getQueue().size());
    }
}
