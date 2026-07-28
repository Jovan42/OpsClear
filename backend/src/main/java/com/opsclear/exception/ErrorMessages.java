package com.opsclear.exception;

public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static final class Project {
        public static final String NOT_FOUND = "Project not found";
        public static final String NAME_ALREADY_EXISTS = "A project with this name already exists";
        public static final String COMPLETED_NO_MUTATIONS = "This project is completed and no longer accepts changes";
        public static final String HAS_OPEN_JOBS =
                "Cannot complete project: %d job(s) are still IN_PROGRESS or BLOCKED";

        private Project() {
        }
    }

    public static final class User {
        public static final String NOT_FOUND = "User not found";

        private User() {
        }
    }

    public static final class Member {
        public static final String NOT_FOUND = "Member not found";
        public static final String NOT_A_MEMBER = "You are not a member of this project";
        public static final String INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN =
                "Insufficient permissions: OWNER or ADMIN role required";
        public static final String INSUFFICIENT_PERMISSIONS_OWNER =
                "Insufficient permissions: OWNER role required";
        public static final String CANNOT_ASSIGN_OWNER_ROLE = "Cannot assign OWNER role";
        public static final String ALREADY_A_MEMBER = "User is already a member of this project";
        public static final String CANNOT_CHANGE_OWNER_ROLE = "Cannot change the project owner's role";
        public static final String CANNOT_REMOVE_OWNER = "Cannot remove the project owner";
        private Member() {
        }
    }

    public static final class Job {
        public static final String NOT_FOUND = "Job not found";
        public static final String ASSIGNED_USER_NOT_FOUND = "Assigned user not found";
        public static final String ACCESS_DENIED_NOT_ASSIGNED =
                "Access denied: you are not assigned to this job";
        public static final String INVALID_TRANSITION = "Invalid transition: ";
        public static final String ONLY_OWNER_ADMIN_CAN_REOPEN =
                "Only OWNER or ADMIN can reopen a completed job";
        public static final String ONLY_OWNER_ADMIN_OR_ASSIGNEE_CAN_CHANGE_STATUS =
                "Only OWNER, ADMIN, or the assigned member can change job status";
        public static final String BLOCK_REASON_REQUIRED =
                "A block reason is required when blocking a job";
        private Job() {
        }
    }

    public static final class BlockReason {
        public static final String NOT_FOUND = "Block reason not found";

        private BlockReason() {
        }
    }

    public static final class Milestone {
        public static final String NOT_FOUND = "Milestone not found";

        private Milestone() {
        }
    }

    public static final class ApiKey {
        public static final String NOT_FOUND = "API key not found";

        private ApiKey() {
        }
    }

    public static final class JobRelationship {
        public static final String NOT_FOUND = "Job relationship not found";
        public static final String SELF_REFERENCE = "A job cannot have a relationship with itself";
        public static final String ALREADY_EXISTS = "This relationship already exists";

        private JobRelationship() {
        }
    }

    public static final class JobTemplate {
        public static final String NOT_FOUND = "Job template not found";
        public static final String ACTIVE_SCHEDULES = "Template is used by active schedules: ";
        public static final String DEFAULT_TYPE_ID_PROJECT_SCOPE_ONLY =
                "defaultTypeId can only be set on project-scoped templates";
        public static final String DEFAULT_TYPE_NAME_ORG_SCOPE_ONLY =
                "defaultTypeName can only be set on org-scoped templates";

        private JobTemplate() {
        }
    }

    public static final class RecurringSchedule {
        public static final String NOT_FOUND = "Recurring schedule not found";
        public static final String INVALID_CRON = "Invalid cron expression: ";
        public static final String CRON_INTERVAL_TOO_SHORT =
                "Cron expression must have a minimum interval of 1 hour";
        public static final String INVALID_TIMEZONE = "Invalid timezone: ";

        private RecurringSchedule() {
        }
    }

    public static final class ScheduleMissedRun {
        public static final String NOT_FOUND = "Missed run not found";

        private ScheduleMissedRun() {
        }
    }

    public static final class Organisation {
        public static final String NOT_FOUND = "Organisation not found";
        public static final String NOT_IN_ORG = "You must belong to an organisation to perform this action";
        public static final String SLUG_ALREADY_EXISTS = "An organisation with this slug already exists";
        public static final String INSUFFICIENT_PERMISSIONS_OWNER =
                "Insufficient permissions: OWNER role required";
        public static final String INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN =
                "Insufficient permissions: OWNER or ADMIN role required";
        public static final String NOT_A_MEMBER = "You are not a member of this organisation";
        public static final String MEMBER_NOT_FOUND = "Member not found in this organisation";
        public static final String ALREADY_A_MEMBER = "User is already a member of this organisation";
        public static final String CANNOT_ASSIGN_OWNER_ROLE = "Cannot assign OWNER role";
        public static final String CANNOT_CHANGE_OWNER_ROLE = "Cannot change the organisation owner's role";
        public static final String CANNOT_REMOVE_OWNER = "Cannot remove the organisation owner";

        private Organisation() {
        }
    }

    public static final class OrgInvite {
        public static final String NOT_FOUND = "Invite not found";
        public static final String ALREADY_PENDING = "A pending invite already exists for this email";
        public static final String EMAIL_ALREADY_MEMBER =
                "This email address is already a member of the organisation";
        public static final String INVALID = "This invite is no longer valid";
        public static final String EMAIL_MISMATCH =
                "This invite was sent to a different email address";

        private OrgInvite() {
        }
    }

    public static final class Approval {
        public static final String NOT_FOUND = "Approval not found";
        public static final String ALREADY_DECIDED = "This approval has already been decided";
        public static final String CANNOT_SET_PENDING = "Cannot transition an approval back to PENDING";
        public static final String ACCESS_DENIED_NOT_ASSIGNED =
                "Access denied: you are not assigned to this job";
        private Approval() {
        }
    }

    public static final class Link {
        public static final String NOT_FOUND = "Link not found";
        public static final String INVALID_URL = "URL is invalid or uses a disallowed scheme";

        private Link() {
        }
    }

    public static final class JobType {
        public static final String NOT_FOUND = "Job type not found";
        public static final String STILL_REFERENCED_BY_JOBS =
                "Cannot delete type: %d job(s) still use this type";
        public static final String STILL_REFERENCED_BY_TEMPLATES =
                "Cannot delete type: %d template(s) still use this type";
        public static final String STILL_REFERENCED_BY_JOBS_AND_TEMPLATES =
                "Cannot delete type: %d job(s) and %d template(s) still use this type";

        private JobType() {
        }
    }
}
