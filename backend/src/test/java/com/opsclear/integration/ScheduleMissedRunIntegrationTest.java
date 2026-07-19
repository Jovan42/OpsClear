package com.opsclear.integration;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import com.opsclear.repository.ScheduleMissedRunRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Missed Runs API")
class ScheduleMissedRunIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RecurringScheduleRepository scheduleRepository;
    @Autowired private ScheduleAssigneeRepository assigneeRepository;
    @Autowired private ScheduleMissedRunRepository missedRunRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobTemplateRepository jobTemplateRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;
    private UUID orgId;
    private UUID templateId;
    private UUID scheduleId;

    @BeforeEach
    void setUp() {
        assigneeRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        missedRunRepository.deleteAll();
        scheduleRepository.deleteAll();
        jobTemplateRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId  = UUID.randomUUID();
        memberId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("MR Org").slug("MRO").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("MR Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Deploy Checklist")
                .title("Deploy {{date}}").assigneeMode("NONE").createdBy(ownerId).build());
        templateId = template.getId();

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("Daily Deploy");
        row.setCronExpression("0 0 12 * * *");
        row.setTimezone("UTC");
        row.setNextRunAt(Instant.parse("2026-01-01T09:00:00Z").atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        scheduleId = scheduleRepository.insert(row).getId();

        UUID schedAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("RECURRING_SCHEDULING"))
                .findFirst().map(a -> a.getId()).orElse(null);
        UUID tierId = tierRepository.findAll().getFirst().getId();
        if (schedAddonId != null) {
            subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(schedAddonId));
        } else {
            subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of());
        }
    }

    private boolean hasAddon() {
        return addonRepository.findAll().stream().anyMatch(a -> a.getKey().equals("RECURRING_SCHEDULING"));
    }

    // -------------------------------------------------------------------------
    // GET missed-runs (list)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("listMissedRuns — owner sees empty list when none exist")
    void listMissedRuns_shouldReturn200Empty_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(get(ApiPaths.missedRuns(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("listMissedRuns — returns recorded missed runs with expected fields")
    void listMissedRuns_shouldReturnMissedRuns_whenPresent() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        Instant expectedAt = Instant.parse("2026-01-01T12:00:00Z");
        missedRunRepository.insert(scheduleId, expectedAt);

        mockMvc.perform(get(ApiPaths.missedRuns(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleId").value(scheduleId.toString()))
                .andExpect(jsonPath("$[0].expectedAt").isString())
                .andExpect(jsonPath("$[0].recordedAt").isString())
                .andExpect(jsonPath("$[0].id").isString());
    }

    @Test
    @DisplayName("listMissedRuns — member can list missed runs")
    void listMissedRuns_shouldReturn200_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(get(ApiPaths.missedRuns(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listMissedRuns — non-member receives 403")
    void listMissedRuns_shouldReturn403_forNonMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(get(ApiPaths.missedRuns(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "alien@example.com"))))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // POST /{missedRunId}/materialize
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("materializeMissedRun — owner materializes missed run and gets 201 job response")
    void materializeMissedRun_shouldReturn201Job_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        UUID missedRunId = missedRunRepository.insert(scheduleId,
                Instant.parse("2026-01-01T12:00:00Z")).getId();

        mockMvc.perform(post(ApiPaths.missedRunMaterialize(projectId, scheduleId, missedRunId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()));

        // Job created in DB
        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).hasSize(1);
        // Missed run consumed (deleted)
        assertThat(missedRunRepository.findByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    @DisplayName("materializeMissedRun — member receives 403")
    void materializeMissedRun_shouldReturn403_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        UUID missedRunId = missedRunRepository.insert(scheduleId,
                Instant.parse("2026-01-01T12:00:00Z")).getId();

        mockMvc.perform(post(ApiPaths.missedRunMaterialize(projectId, scheduleId, missedRunId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("materializeMissedRun — 404 for unknown missedRunId")
    void materializeMissedRun_shouldReturn404_forUnknownMissedRunId() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.missedRunMaterialize(projectId, scheduleId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /{missedRunId} (dismiss)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("dismissMissedRun — owner dismisses and receives 204, row is deleted")
    void dismissMissedRun_shouldReturn204_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        UUID missedRunId = missedRunRepository.insert(scheduleId,
                Instant.parse("2026-01-01T12:00:00Z")).getId();

        mockMvc.perform(delete(ApiPaths.missedRun(projectId, scheduleId, missedRunId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(missedRunRepository.findByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    @DisplayName("dismissMissedRun — member receives 403")
    void dismissMissedRun_shouldReturn403_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        UUID missedRunId = missedRunRepository.insert(scheduleId,
                Instant.parse("2026-01-01T12:00:00Z")).getId();

        mockMvc.perform(delete(ApiPaths.missedRun(projectId, scheduleId, missedRunId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("dismissMissedRun — 404 for unknown missedRunId")
    void dismissMissedRun_shouldReturn404_forUnknownId() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(delete(ApiPaths.missedRun(projectId, scheduleId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE (dismiss-all)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("dismissAllMissedRuns — owner dismisses all and receives 204, all rows deleted")
    void dismissAllMissedRuns_shouldReturn204_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        missedRunRepository.insert(scheduleId, Instant.parse("2026-01-01T12:00:00Z"));
        missedRunRepository.insert(scheduleId, Instant.parse("2026-01-02T12:00:00Z"));

        mockMvc.perform(delete(ApiPaths.missedRuns(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(missedRunRepository.findByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    @DisplayName("dismissAllMissedRuns — member receives 403")
    void dismissAllMissedRuns_shouldReturn403_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(delete(ApiPaths.missedRuns(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("dismissAllMissedRuns — 204 even when there are no missed runs")
    void dismissAllMissedRuns_shouldReturn204_whenNoneExist() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(delete(ApiPaths.missedRuns(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());
    }
}
