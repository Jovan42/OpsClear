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

    public static final class Approval {
        public static final String NOT_FOUND = "Approval not found";
        public static final String ALREADY_DECIDED = "This approval has already been decided";
        public static final String CANNOT_SET_PENDING = "Cannot transition an approval back to PENDING";
        public static final String ACCESS_DENIED_NOT_ASSIGNED =
                "Access denied: you are not assigned to this job";
        private Approval() {
        }
    }
}
