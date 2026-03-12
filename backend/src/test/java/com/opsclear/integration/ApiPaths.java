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

    static String jobsBySearchAndPriority(UUID projectId, String q, String priority) {
        return "/api/projects/" + projectId + "/jobs?q=" + q + "&priority=" + priority;
    }

    static String job(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId;
    }

    static String jobStatus(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/status";
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

    // --- Dashboard ---

    static String dashboard(UUID projectId) {
        return "/api/projects/" + projectId + "/dashboard";
    }

    private ApiPaths() {
    }
}
