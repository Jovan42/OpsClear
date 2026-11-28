package com.opsclear.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Free trial settings (ADR-0067). Set in {@code application.properties}, each overridable by env
 * ({@code OPSCLEAR_TRIAL_DAYS}, {@code OPSCLEAR_TRIAL_MAX_MEMBERS}, {@code OPSCLEAR_TRIAL_MAX_PROJECTS}).
 * Changing them affects new trials only: existing orgs keep their {@code trial_ends_at}.
 *
 * @param days        trial length for a new organisation
 * @param maxMembers  member cap during the trial (and while locked), including the owner
 * @param maxProjects active project cap during the trial (and while locked)
 */
@Validated
@ConfigurationProperties("opsclear.trial")
public record TrialProperties(@Positive int days, @Positive int maxMembers, @Positive int maxProjects) {
}
