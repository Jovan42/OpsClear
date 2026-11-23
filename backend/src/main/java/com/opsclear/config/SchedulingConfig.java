package com.opsclear.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs. {@code opsclear.scheduling.enabled=false} (test profile) switches them off, so a
 * poller or alert job running on a background thread, in this or in another cached test context sharing the same
 * database, can't write rows while an integration test cleans up. Tests call the jobs directly instead. On by
 * default, so production is unchanged.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "opsclear.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
