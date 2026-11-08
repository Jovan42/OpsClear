package com.opsclear.integration;

import com.opsclear.config.AsyncConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code defaultCandidate = false} registration of {@code notificationExecutor} (JOB-276): Spring Boot's
 * {@code applicationTaskExecutor} (Spring MVC async, SSE) must still be auto-configured alongside it. A Boot upgrade
 * that changes how {@code @ConditionalOnMissingBean} treats non-default candidates would fail here.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Async executors in the application context")
class AsyncConfigIntegrationTest {

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("applicationTaskExecutor still exists alongside notificationExecutor")
    void applicationTaskExecutor_shouldExistAlongsideNotificationExecutor() {
        assertThat(context.containsBean(AsyncConfig.NOTIFICATION_EXECUTOR)).isTrue();
        assertThat(context.getBean(AsyncConfig.NOTIFICATION_EXECUTOR)).isInstanceOf(TaskExecutor.class);
        assertThat(context.containsBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)).isTrue();
        assertThat(context.getBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME))
                .isInstanceOf(AsyncTaskExecutor.class);
    }
}
