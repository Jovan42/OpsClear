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

    // --- Job types ---

    static String jobTypes(UUID projectId) {
        return "/api/projects/" + projectId + "/job-types";
    }

    static String jobTypes(String projectId) {
        return "/api/projects/" + projectId + "/job-types";
    }

    static String jobType(UUID projectId, UUID typeId) {
        return "/api/projects/" + projectId + "/job-types/" + typeId;
    }

    static String jobsByType(UUID projectId, UUID typeId) {
        return "/api/projects/" + projectId + "/jobs?typeId=" + typeId;
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

    // --- Subscription catalog ---

    static final String SUBSCRIPTION_CATALOG = "/api/subscriptions/catalog";

    // --- Org subscription ---

    static String orgSubscription(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription";
    }

    // --- Super admin pricing ---

    static final String SUPER_ADMIN_PRICING_TIERS  = "/api/super-admin/pricing/tiers";
    static final String SUPER_ADMIN_PRICING_ADDONS = "/api/super-admin/pricing/addons";

    static String superAdminPricingTier(UUID tierId) {
        return "/api/super-admin/pricing/tiers/" + tierId;
    }

    static String superAdminPricingAddon(String addonKey) {
        return "/api/super-admin/pricing/addons/" + addonKey;
    }

    // --- Feedback ---

    static final String FEEDBACK      = "/api/feedback";
    static final String FEEDBACK_MINE = "/api/feedback/mine";

    // --- Credits ---

    static String orgCreditsBalance(UUID orgId) {
        return "/api/organisations/" + orgId + "/credits/balance";
    }

    // --- Super admin feedback + credits ---

    static final String SUPER_ADMIN_FEEDBACK      = "/api/super-admin/feedback";
    static final String SUPER_ADMIN_CREDITS_GRANT = "/api/super-admin/credits/grant";

    static String superAdminOrgCredits(UUID orgId) {
        return "/api/super-admin/organisations/" + orgId + "/credits";
    }

    // --- Dashboard ---

    static String dashboard(UUID projectId) {
        return "/api/projects/" + projectId + "/dashboard";
    }

    // --- Job templates ---

    static String templates(UUID projectId) {
        return "/api/projects/" + projectId + "/templates";
    }

    static String template(UUID projectId, UUID templateId) {
        return "/api/projects/" + projectId + "/templates/" + templateId;
    }

    static String template(String projectId, String templateId) {
        return "/api/projects/" + projectId + "/templates/" + templateId;
    }

    static String templateUse(UUID projectId, UUID templateId) {
        return "/api/projects/" + projectId + "/templates/" + templateId + "/use";
    }

    // --- Org templates ---

    static String orgTemplates(UUID orgId) {
        return "/api/organisations/" + orgId + "/templates";
    }

    static String orgTemplate(UUID orgId, UUID templateId) {
        return "/api/organisations/" + orgId + "/templates/" + templateId;
    }

    // --- Recurring schedules ---

    static String schedules(UUID projectId) {
        return "/api/projects/" + projectId + "/schedules";
    }

    static String schedule(UUID projectId, UUID scheduleId) {
        return "/api/projects/" + projectId + "/schedules/" + scheduleId;
    }

    static String schedulePause(UUID projectId, UUID scheduleId) {
        return "/api/projects/" + projectId + "/schedules/" + scheduleId + "/pause";
    }

    static String scheduleResume(UUID projectId, UUID scheduleId) {
        return "/api/projects/" + projectId + "/schedules/" + scheduleId + "/resume";
    }

    // --- Missed runs ---

    static String missedRuns(UUID projectId, UUID scheduleId) {
        return "/api/projects/" + projectId + "/schedules/" + scheduleId + "/missed-runs";
    }

    static String missedRun(UUID projectId, UUID scheduleId, UUID missedRunId) {
        return "/api/projects/" + projectId + "/schedules/" + scheduleId + "/missed-runs/" + missedRunId;
    }

    static String missedRunMaterialize(UUID projectId, UUID scheduleId, UUID missedRunId) {
        return missedRun(projectId, scheduleId, missedRunId) + "/materialize";
    }

    // --- Cron preview ---

    static final String CRON_PREVIEW = "/api/schedules/preview";

    // --- Job links ---

    static String jobLinks(UUID projectId, UUID jobId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/links";
    }

    static String jobLink(UUID projectId, UUID jobId, UUID linkId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/links/" + linkId;
    }

    // --- Project links ---

    static String projectLinks(UUID projectId) {
        return "/api/projects/" + projectId + "/links";
    }

    static String projectLink(UUID projectId, UUID linkId) {
        return "/api/projects/" + projectId + "/links/" + linkId;
    }

    private ApiPaths() {
    }
}
