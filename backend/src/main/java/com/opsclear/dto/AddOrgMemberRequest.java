package com.opsclear.dto;

import com.opsclear.model.OrganisationRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddOrgMemberRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Role is required")
    private OrganisationRole role;
}
