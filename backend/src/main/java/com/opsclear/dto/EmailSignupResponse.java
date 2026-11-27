package com.opsclear.dto;

import com.opsclear.model.EmailSignupModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSignupResponse {

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

    public static EmailSignupResponse from(EmailSignupModel model) {
        return EmailSignupResponse.builder()
                .id(model.getId())
                .list(model.getList())
                .email(model.getEmail())
                .code(model.getCode())
                .redeemedAt(model.getRedeemedAt())
                .redeemedOrgId(model.getRedeemedOrgId())
                .locale(model.getLocale())
                .utmSource(model.getUtmSource())
                .utmMedium(model.getUtmMedium())
                .utmCampaign(model.getUtmCampaign())
                .consentAt(model.getConsentAt())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
