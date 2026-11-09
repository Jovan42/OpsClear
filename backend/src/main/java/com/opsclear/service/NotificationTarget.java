package com.opsclear.service;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectModel;

import java.time.Instant;
import java.util.ArrayList;
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

        /**
         * Who can currently see the job: Owners/Admins plus the assignee. Removing a member doesn't unassign their
         * jobs, so the assignee only counts while still a project member ({@code assigneeIsMember}).
         */
        static JobScope of(JobModel job, ProjectModel project, List<UUID> ownerAndAdminIds, boolean assigneeIsMember) {
            Set<UUID> visible = new LinkedHashSet<>(ownerAndAdminIds);
            if (assigneeIsMember) {
                visible.add(job.getAssignedTo());
            }
            return new JobScope(job, project, ownerAndAdminIds, visible);
        }

        NotificationPayload payload(String actorName) {
            return NotificationPayload.forJob(job, project, actorName);
        }

        /** The assignee (null when unassigned, dropped by the pipeline) plus the project's Owners and Admins. */
        List<UUID> assigneeAndOwnersAndAdmins() {
            List<UUID> candidates = new ArrayList<>();
            candidates.add(job.getAssignedTo());
            candidates.addAll(ownerAndAdminIds);
            return candidates;
        }

        /** Whether the job is still open, in an active project, with this deadline (deadline alerts, JOB-278). */
        boolean isDueOn(Instant deadline) {
            return job.getStatus() != JobStatus.COMPLETED && !project.isCompleted()
                    && deadline.equals(job.getDeadline());
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
