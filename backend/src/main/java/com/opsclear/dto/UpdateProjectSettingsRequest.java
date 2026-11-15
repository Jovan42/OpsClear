package com.opsclear.dto;

import com.opsclear.model.JobSection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Partial update of per-project settings ({@code PATCH /api/projects/{id}/settings}, ADR-0052, ADR-0053).
 * Every field is nullable: {@code null} (or absent) leaves that setting unchanged.
 * An empty {@code hiddenJobSections} list shows every section again.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectSettingsRequest {

    private Boolean requireCompletionApproval;

    private List<JobSection> hiddenJobSections;
}
