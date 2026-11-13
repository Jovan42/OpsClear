package com.opsclear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Partial update of per-project settings ({@code PATCH /api/projects/{id}/settings}, ADR-0052).
 * Every field is nullable: {@code null} (or absent) leaves that setting unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectSettingsRequest {

    private Boolean requireCompletionApproval;
}
