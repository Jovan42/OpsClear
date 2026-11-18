package com.opsclear.dto;

import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.OrgSettingsModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgSettingsResponse {

    private String projectPrefix;
    private String jobPrefix;
    private String milestonePrefix;
    private String templatePrefix;
    private String nextProjectId;
    private String nextJobId;
    private String nextMilestoneId;
    private String nextTemplateId;

    public static OrgSettingsResponse from(OrgSettingsModel model) {
        return OrgSettingsResponse.builder()
                .projectPrefix(model.getProjectPrefix())
                .jobPrefix(model.getJobPrefix())
                .milestonePrefix(model.getMilestonePrefix())
                .templatePrefix(model.getTemplatePrefix())
                .nextProjectId(model.getNextIds().get(FriendlyIdEntityType.PROJECT))
                .nextJobId(model.getNextIds().get(FriendlyIdEntityType.JOB))
                .nextMilestoneId(model.getNextIds().get(FriendlyIdEntityType.MILESTONE))
                .nextTemplateId(model.getNextIds().get(FriendlyIdEntityType.TEMPLATE))
                .build();
    }
}
