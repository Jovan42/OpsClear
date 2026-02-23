package com.opsclear.dto;

import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
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
public class ProjectMemberResponse {

    private UUID id;
    private UUID projectId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private ProjectMemberRole role;
    private Instant joinedAt;

    public static ProjectMemberResponse from(ProjectMemberModel member) {
        return ProjectMemberResponse.builder()
                .id(member.getId())
                .projectId(member.getProjectId())
                .userId(member.getUserId())
                .userName(member.getUserName())
                .userEmail(member.getUserEmail())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
