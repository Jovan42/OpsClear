package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One email on one sign-up list (ADR-0066). Hard-deleted, so there's no {@code deletedAt}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSignupModel {

    private UUID id;
    private String list;
    private String email;
    private String code;
    private Instant redeemedAt;
    private UUID redeemedOrgId;
    private String locale;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private Instant consentAt;
    private Instant createdAt;
}
