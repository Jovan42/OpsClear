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

    static String projectSettings(UUID projectId) {
        return "/api/projects/" + projectId + "/settings";
    }

    static String projectSettings(String projectId) {
        return "/api/projects/" + projectId + "/settings";
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

    static String cancelApproval(UUID projectId, UUID jobId, UUID approvalId) {
        return "/api/projects/" + projectId + "/jobs/" + jobId + "/approvals/" + approvalId + "/cancel";
    }

    static String pendingApprovals(UUID projectId) {
        return "/api/projects/" + projectId + "/approvals/pending";
    }

    static final String PENDING_APPROVALS_ACROSS_ORGS = "/api/approvals/pending";

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

    // --- Paddle subscription ---

    static String paddleSubscription(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription/paddle";
    }

    static String paddleSubscriptionCancel(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription/paddle/cancel";
    }

    static String paddleSubscriptionUpdatePaymentMethodTransaction(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription/paddle/update-payment-method-transaction";
    }

    static String paddleSubscriptionResume(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription/paddle/resume";
    }

    static String paddleSubscriptionPreview(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription/paddle/preview";
    }

    static String paddleSubscriptionCancelPendingDowngrade(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription/paddle/cancel-pending-downgrade";
    }

    static String paddleSubscriptionTransactions(UUID orgId) {
        return "/api/organisations/" + orgId + "/subscription/paddle/transactions";
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

    static final String SUPER_ADMIN_PRICING_SYNC = "/api/super-admin/pricing/sync";

    // --- Feedback ---

    static final String FEEDBACK      = "/api/feedback";
    static final String FEEDBACK_MINE = "/api/feedback/mine";

    // --- Credits ---

    static String orgCreditsBalance(UUID orgId) {
        return "/api/organisations/" + orgId + "/credits/balance";
    }

    // --- Project directory ---

    static String projectDirectory(UUID orgId) {
        return "/api/organisations/" + orgId + "/projects/directory";
    }

    // --- Super admin feedback + credits ---

    static final String SUPER_ADMIN_FEEDBACK      = "/api/super-admin/feedback";
    static final String SUPER_ADMIN_CREDITS_GRANT = "/api/super-admin/credits/grant";
    static final String SUPER_ADMIN_ORGANISATIONS = "/api/super-admin/organisations";

    static String superAdminFeedbackDecline(UUID id) {
        return "/api/super-admin/feedback/" + id + "/decline";
    }

    static String superAdminOrgCredits(UUID orgId) {
        return "/api/super-admin/organisations/" + orgId + "/credits";
    }

    // --- Dashboard ---

    static String dashboard(UUID projectId) {
        return "/api/projects/" + projectId + "/dashboard";
    }

    // --- Stats ---

    static String stats(UUID projectId) {
        return stats(projectId.toString());
    }

    static String stats(String projectId) {
        return "/api/projects/" + projectId + "/stats";
    }

    /** Stats with query params; {@code null} params are left out. Strings so tests can send invalid values. */
    static String stats(UUID projectId, String weeks, String tz, String memberId) {
        StringBuilder query = new StringBuilder();
        appendParam(query, "weeks", weeks);
        appendParam(query, "tz", tz);
        appendParam(query, "memberId", memberId);
        return stats(projectId) + query;
    }

    static String statsWeeks(UUID projectId, String weeks) {
        return stats(projectId, weeks, null, null);
    }

    static String statsTz(UUID projectId, String tz) {
        return stats(projectId, null, tz, null);
    }

    static String statsForMember(UUID projectId, UUID memberId) {
        return stats(projectId, null, null, memberId.toString());
    }

    private static void appendParam(StringBuilder query, String name, String value) {
        if (value != null) {
            query.append(query.isEmpty() ? '?' : '&').append(name).append('=').append(value);
        }
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

    // --- Paddle webhooks ---

    static final String WEBHOOKS_PADDLE = "/api/webhooks/paddle";

    // --- Project links ---

    static String projectLinks(UUID projectId) {
        return "/api/projects/" + projectId + "/links";
    }

    static String projectLink(UUID projectId, UUID linkId) {
        return "/api/projects/" + projectId + "/links/" + linkId;
    }

    // --- Notifications ---

    static final String NOTIFICATIONS = "/api/notifications";
    static final String NOTIFICATIONS_UNREAD_COUNT = "/api/notifications/unread-count";
    static final String NOTIFICATIONS_READ_ALL = "/api/notifications/read-all";
    static final String NOTIFICATION_SETTINGS = "/api/user/notification-settings";

    /** List with a raw query string, e.g. {@code "unread=true&page=0&size=10"}. */
    static String notifications(String query) {
        return "/api/notifications?" + query;
    }

    static String notification(UUID notificationId) {
        return "/api/notifications/" + notificationId;
    }

    static String notification(String notificationId) {
        return "/api/notifications/" + notificationId;
    }

    static String notificationStream() {
        return "/api/notifications/stream";
    }

    private ApiPaths() {
    }
}
