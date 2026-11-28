package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An organisation's member and active project caps (ADR-0067). {@code null} means unlimited. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgLimits {

    private Integer maxMembers;
    private Integer maxProjects;

    public static OrgLimits unlimited() {
        return new OrgLimits(null, null);
    }
}
