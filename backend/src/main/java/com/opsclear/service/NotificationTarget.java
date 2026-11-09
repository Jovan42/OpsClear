package com.opsclear.service;

import com.opsclear.model.JobModel;
import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectModel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Where a fan-out's notifications point (organisation, optional project and job) and who may receive them. The
 * pipeline in {@code NotificationService.fanOut} only needs this, so job, project and organisation-level types
 * share it. Package-private: only {@link NotificationService} uses these types.
 */
sealed interface NotificationTarget permits NotificationTarget.JobScope, NotificationTarget.OrgScope {

    UUID orgId();

    UUID projectId();

    UUID jobId();

    Set<UUID> visibleUserIds();

    String describe();

    /**
     * The job a notification is about, its project, its Owners/Admins, and who can currently see it (Owners/Admins
     * plus the assignee while they're still a project member).
     */
    record JobScope(JobModel job, ProjectModel project, List<UUID> ownerAndAdminIds,
                    Set<UUID> visibleUserIds) implements NotificationTarget {

        NotificationPayload payload(String actorName) {
            return NotificationPayload.forJob(job, project, actorName);
        }

        @Override
        public UUID orgId() {
            return project.getOrganisationId();
        }

        @Override
        public UUID projectId() {
            return project.getId();
        }

        @Override
        public UUID jobId() {
            return job.getId();
        }

        @Override
        public String describe() {
            return "job " + job.getId();
        }
    }

    /**
     * A notification without a job (JOB-279): organisation-level ({@code projectId} null) or project-level. The
     * caller decides who may receive it ({@code visibleUserIds}).
     */
    record OrgScope(UUID orgId, UUID projectId, Set<UUID> visibleUserIds) implements NotificationTarget {

        @Override
        public UUID jobId() {
            return null;
        }

        @Override
        public String describe() {
            return projectId != null ? "project " + projectId : "organisation " + orgId;
        }
    }

    /** An organisation and its current members, for resolving organisation-level recipients. */
    record OrgMembers(OrganisationModel org, List<OrgMemberModel> members) {

        Set<UUID> memberIds() {
            Set<UUID> ids = new LinkedHashSet<>();
            members.forEach(m -> ids.add(m.getUserId()));
            return ids;
        }

        List<UUID> ownerAndAdminIds() {
            return members.stream()
                    .filter(m -> m.getRole() != OrganisationRole.MEMBER)
                    .map(OrgMemberModel::getUserId)
                    .toList();
        }

        Optional<OrgMemberModel> member(UUID userId) {
            return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst();
        }

        NotificationPayload payload(String actorName) {
            return NotificationPayload.forOrganisation(org, actorName);
        }
    }
}
