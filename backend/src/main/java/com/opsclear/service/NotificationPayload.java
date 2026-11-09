package com.opsclear.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.JobModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.ProjectModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the display snapshot stored in {@code notifications.payload} (ADR-0051), so a notification still reads
 * correctly after the job is renamed or deleted. The frontend renders text from the type and these keys. Null values
 * are left out.
 */
final class NotificationPayload {

    static final String JOB_FRIENDLY_ID = "jobFriendlyId";
    static final String JOB_TITLE = "jobTitle";
    static final String PROJECT_FRIENDLY_ID = "projectFriendlyId";
    static final String PROJECT_NAME = "projectName";
    static final String ACTOR_NAME = "actorName";
    static final String DESCRIPTION = "description";
    static final String DECISION = "decision";
    static final String COMMENT = "comment";
    // Job lifecycle types (JOB-277). Deadlines are ISO-8601 instants.
    static final String DEADLINE = "deadline";
    static final String PRIORITY = "priority";
    static final String FROM_STATUS = "fromStatus";
    static final String TO_STATUS = "toStatus";
    static final String BLOCK_REASON = "blockReason";
    static final String BLOCKER_JOB_FRIENDLY_ID = "blockerJobFriendlyId";
    static final String BLOCKER_JOB_TITLE = "blockerJobTitle";
    static final String CHANGED_FIELDS = "changedFields";
    // Project, organisation and billing types (JOB-279)
    static final String ORG_NAME = "orgName";
    static final String ROLE = "role";
    static final String MEMBER_NAME = "memberName";
    static final String MEMBER_EMAIL = "memberEmail";
    static final String FEEDBACK_TITLE = "feedbackTitle";
    static final String OUTCOME = "outcome";
    static final String CREDIT_AMOUNT = "creditAmount";

    private final Map<String, Object> values = new LinkedHashMap<>();

    private NotificationPayload() {
    }

    /** Snapshot shared by every job-related type: job friendly ID and title, project friendly ID and name, actor. */
    static NotificationPayload forJob(JobModel job, ProjectModel project, String actorName) {
        return new NotificationPayload()
                .put(JOB_FRIENDLY_ID, job.getFriendlyId())
                .put(JOB_TITLE, job.getTitle())
                .put(PROJECT_FRIENDLY_ID, project.getFriendlyId())
                .put(PROJECT_NAME, project.getName())
                .put(ACTOR_NAME, actorName);
    }

    /** Snapshot for project-level types without a job: project friendly ID and name, actor. */
    static NotificationPayload forProject(ProjectModel project, String actorName) {
        return new NotificationPayload()
                .put(PROJECT_FRIENDLY_ID, project.getFriendlyId())
                .put(PROJECT_NAME, project.getName())
                .put(ACTOR_NAME, actorName);
    }

    /** Snapshot for organisation-level types (no project): organisation name, actor. */
    static NotificationPayload forOrganisation(OrganisationModel organisation, String actorName) {
        return new NotificationPayload()
                .put(ORG_NAME, organisation.getName())
                .put(ACTOR_NAME, actorName);
    }

    /** Adds a type-specific field; a null value is skipped. */
    NotificationPayload put(String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
        return this;
    }

    String toJson(ObjectMapper objectMapper) {
        return objectMapper.valueToTree(values).toString();
    }
}
