package com.opsclear.dto;

import com.opsclear.model.OrganisationRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOrgMemberRoleRequest {

    @NotNull(message = "Role is required")
    private OrganisationRole role;
}
