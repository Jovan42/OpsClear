package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** An organisation's access state, limits and usage, as shown in the organisation response (ADR-0067). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgAccessModel {

    private OrgAccessState state;
    private Instant trialEndsAt;
    private Integer maxMembers;
    private Integer maxProjects;
    private int memberCount;
    private int pendingInviteCount;
    private int activeProjectCount;
    private boolean lockoutEnabled;
}
