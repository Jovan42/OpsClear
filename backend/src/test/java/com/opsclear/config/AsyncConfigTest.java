package com.opsclear.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("AsyncConfig notificationExecutor")
class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    @DisplayName("is a bounded pool: core 2, max 4, queue 1,000, log-and-drop on rejection")
    void notificationExecutor_shouldBeBoundedPool() {
        TaskExecutor executor = asyncConfig.notificationExecutor(false);

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        pool.initialize();
        try {
            assertThat(pool.getCorePoolSize()).isEqualTo(2);
            assertThat(pool.getMaxPoolSize()).isEqualTo(4);
            assertThat(pool.getQueueCapacity()).isEqualTo(1_000);
            assertThat(pool.getThreadNamePrefix()).isEqualTo("notification-");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("is synchronous when notifications.synchronous is true (test profile)")
    void notificationExecutor_shouldBeSynchronous_whenConfigured() {
        assertThat(asyncConfig.notificationExecutor(true)).isInstanceOf(SyncTaskExecutor.class);
    }

    @Test
    @DisplayName("a saturated pool logs a warning and drops the task instead of throwing")
    void notificationExecutor_shouldLogAndDrop_whenSaturated(CapturedOutput output) throws Exception {
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) asyncConfig.notificationExecutor(false);
        pool.initialize();
        CountDownLatch release = new CountDownLatch(1);
        try {
            // Fill every thread and queue slot with tasks that wait, so the next submit is rejected.
            int capacity = AsyncConfig.MAX_POOL_SIZE + AsyncConfig.QUEUE_CAPACITY;
            for (int i = 0; i < capacity; i++) {
                pool.execute(() -> awaitQuietly(release));
            }
            AtomicBoolean ran = new AtomicBoolean(false);

            assertThatCode(() -> pool.execute(() -> ran.set(true))).doesNotThrowAnyException();

            ThreadPoolExecutor executor = pool.getThreadPoolExecutor();
            assertThat(executor.getQueue()).hasSize(AsyncConfig.QUEUE_CAPACITY);
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(ran).isFalse();
            assertThat(output).contains("Notification executor saturated").contains("dropping a notification event");
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
