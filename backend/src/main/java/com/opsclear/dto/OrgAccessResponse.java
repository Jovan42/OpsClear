package com.opsclear.dto;

import com.opsclear.model.OrgAccessModel;
import com.opsclear.model.OrgAccessState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code access} in the organisation response (ADR-0067). {@code maxMembers} / {@code maxProjects} null = unlimited.
 * {@code lockoutEnabled} false means a {@code LOCKED} org isn't blocked yet, so the frontend shows "contact us"
 * instead of the paywall.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgAccessResponse {

    private OrgAccessState state;
    private Instant trialEndsAt;
    private Integer maxMembers;
    private Integer maxProjects;
    private int memberCount;
    private int pendingInviteCount;
    private int activeProjectCount;
    private boolean lockoutEnabled;

    public static OrgAccessResponse from(OrgAccessModel access) {
        return OrgAccessResponse.builder()
                .state(access.getState())
                .trialEndsAt(access.getTrialEndsAt())
                .maxMembers(access.getMaxMembers())
                .maxProjects(access.getMaxProjects())
                .memberCount(access.getMemberCount())
                .pendingInviteCount(access.getPendingInviteCount())
                .activeProjectCount(access.getActiveProjectCount())
                .lockoutEnabled(access.isLockoutEnabled())
                .build();
    }
}
