package com.opsclear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The lock-out switch (ADR-0067 amendment, 2026-11-26). Overridable by env ({@code OPSCLEAR_LOCKOUT_ENABLED}).
 * While {@code false}, an org whose trial has ended is still reported as {@code LOCKED} but gets no 402 and keeps
 * add-on access. Turned on when Paddle is live (JOB-432).
 *
 * @param enabled {@code true} blocks locked orgs with 402 {@code OrgLocked}
 */
@ConfigurationProperties("opsclear.lockout")
public record LockoutProperties(boolean enabled) {
}
