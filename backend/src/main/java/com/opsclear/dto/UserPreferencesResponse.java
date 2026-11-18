package com.opsclear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** The caller's stored UI preferences (ADR-0061). {@code ui} is {@code {}} when nothing has been saved. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferencesResponse {

    private Map<String, Object> ui;

    public static UserPreferencesResponse from(Map<String, Object> ui) {
        return UserPreferencesResponse.builder()
                .ui(ui)
                .build();
    }
}
