package com.opsclear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * A partial update of the caller's UI preferences (ADR-0061): top-level keys are merged into the stored {@code ui},
 * a {@code null} value removes the key. Opaque by design, so keys, values and limits are validated by
 * UserPreferencesService rather than with Jakarta annotations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserPreferencesRequest {

    private Map<String, Object> ui;
}
