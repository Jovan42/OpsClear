package com.opsclear.integration;

import java.util.UUID;

/**
 * Centralised URL builder for integration tests.
 * Keeps endpoint strings in one place so renaming a route only requires a single edit.
 */
final class ApiPaths {

    static final String HEALTH    = "/api/health";
    static final String PROTECTED = "/api/protected";
    static final String PROJECTS  = "/api/projects";
    static final String USERS     = "/api/users";

    // --- Users ---

    static String usersSearch(String emailPrefix) {
        return "/api/users?email=" + emailPrefix;
    }

    // --- Projects ---

    static String project(UUID projectId) {
        return "/api/projects/" + projectId;
    }

    static String project(String projectId) {
        return "/api/projects/" + projectId;
    }

    static String projectStatus(UUID projectId) {
        return "/api/projects/" + projectId + "/status";
    }

    static String projectsByStatus(String status) {
        return "/api/projects?status=" + status;
    }

    // --- Project members ---

    static String members(UUID projectId) {
        return "/api/projects/" + projectId + "/members";
    }

    static String member(UUID projectId, UUID memberId) {
        return "/api/projects/" + projectId + "/members/" + memberId;
    }

    // --- Block reasons ---

    static String blockReasons(UUID projectId) {
        return "/api/projects/" + projectId + "/block-reasons";
    }

    static String blockReason(UUID projectId, UUID reasonId) {
        return "/api/projects/" + projectId + "/block-reasons/" + reasonId;
    }

    // --- Jobs ---

    static String jobs(UUID projectId) {
        return "/api/projects/" + projectId + "/jobs";
    }

    static String jobsSearch(UUID projectId, String q) {
        return "/api/projects/" + projectId + "/jobs?q=" + q;
    }

    static String jobsByPriority(UUID projectId, String priority) {
        return "/api/projects/" + projectId + "/jobs?priority=" + priority;
    }

    static String jobsBySearchAndPriority(UUID projectId) {
        return "/api/projects/" + projectId + "/jobs?q=login&priority=CRITICAL";
    }

    static String jobsByMilestone(UUID projectId, UUID milestoneId) {
        return "/api/projects/" + projectId + "/jobs?milestoneId=" + milestoneId;
    }

    static String job(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId;
    }

    static String job(String projectId, String jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId;
    }

    static String jobStatus(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/status";
    }

    // --- Job relationships ---

    static String jobHistory(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/history";
    }

    static String jobRelationships(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/relationships";
    }

    static String jobRelationship(UUID projectId, UUID jobId, UUID relationshipId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/relationships/" + relationshipId;
    }

    // --- Notes ---

    static String notes(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/notes";
    }

    static String projectNotes(UUID projectId) {
        return "/api/projects/" + projectId + "/notes";
    }

    // --- Approvals ---

    static String approvals(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/approvals";
    }

    static String approvalStatus(UUID projectId, UUID jobId, UUID approvalId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/approvals/" + approvalId + "/status";
    }

    static String pendingApprovals(UUID projectId) {
        return "/api/projects/" + projectId + "/approvals/pending";
    }

    // --- Milestones ---

    static String milestones(UUID projectId) {
        return "/api/projects/" + projectId + "/milestones";
    }

    static String milestones(String projectId) {
        return "/api/projects/" + projectId + "/milestones";
    }

    static String milestone(UUID projectId, UUID milestoneId) {
        return "/api/projects/" + projectId + "/milestones/" + milestoneId;
    }

    static String milestone(String projectId, String milestoneId) {
        return "/api/projects/" + projectId + "/milestones/" + milestoneId;
    }

    // --- API keys ---

    static final String API_KEYS = "/api/user/api-keys";

    static String apiKey(UUID keyId) {
        return "/api/user/api-keys/" + keyId;
    }

    // --- Organisations ---

    static final String ORGANISATIONS = "/api/organisations";
    static final String MY_ORG        = "/api/organisations/mine";

    static String organisation(UUID orgId) {
        return "/api/organisations/" + orgId;
    }

    static String orgMembers(UUID orgId) {
        return "/api/organisations/" + orgId + "/members";
    }

    static String orgMember(UUID orgId, UUID userId) {
        return "/api/organisations/" + orgId + "/members/" + userId;
    }

    // --- Organisation invites ---

    static String orgInvites(UUID orgId) {
        return "/api/organisations/" + orgId + "/invites";
    }

    static String orgInvite(UUID orgId, UUID inviteId) {
        return "/api/organisations/" + orgId + "/invites/" + inviteId;
    }

    static String acceptInvite(String token) {
        return "/api/invites/" + token + "/accept";
    }

    // --- Dashboard ---

    static String dashboard(UUID projectId) {
        return "/api/projects/" + projectId + "/dashboard";
    }

    private ApiPaths() {
    }
}
