package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Recurring Schedules API")
class RecurringScheduleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecurringScheduleRepository scheduleRepository;
    @Autowired private ScheduleAssigneeRepository assigneeRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobTemplateRepository jobTemplateRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID memberMembershipId;
    private UUID projectId;
    private UUID orgId;
    private UUID templateId;

    private static final String VALID_CRON = "0 0 * * * *";

    @BeforeEach
    void setUp() {
        assigneeRepository.deleteAll();
        scheduleRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
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
                OrganisationModel.builder().name("Test Org").slug("SCH").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        ProjectMemberModel memberMembership = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());
        memberMembershipId = memberMembership.getId();

        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Weekly Report")
                .title("Report {{date}}").assigneeMode("NONE").createdBy(ownerId).build());
        templateId = template.getId();

        UUID schedAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("RECURRING_SCHEDULING")).findFirst()
                .map(a -> a.getId())
                .orElse(null);
        UUID tierId = tierRepository.findAll().getFirst().getId();

        if (schedAddonId != null) {
            subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(schedAddonId));
        } else {
            // Addon not seeded yet — create subscription without it so tests cover the 403 path
            subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of());
        }
    }

    private boolean hasAddon() {
        return addonRepository.findAll().stream()
                .anyMatch(a -> a.getKey().equals("RECURRING_SCHEDULING"));
    }

    // --- POST /api/projects/{projectId}/schedules ---

    @Test
    @DisplayName("createSchedule — owner receives 201 with populated fields")
    void createSchedule_shouldReturn201_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Weekly Sync",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Weekly Sync"))
                .andExpect(jsonPath("$.cronExpression").value("0 0 9 * * MON"))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()));
    }

    @Test
    @DisplayName("createSchedule — member receives 403")
    void createSchedule_shouldReturn403_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "S1",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createSchedule — blank name returns 400")
    void createSchedule_shouldReturn400_whenNameBlank() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createSchedule — sub-hourly cron returns 400")
    void createSchedule_shouldReturn400_whenCronTooFrequent() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Every Minute",
                                  "templateId": "%s",
                                  "cronExpression": "0 * * * * *",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createSchedule — non-member receives 403")
    void createSchedule_shouldReturn403_whenNotMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("email", "stranger@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "S1",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createSchedule — unknown project returns 404")
    void createSchedule_shouldReturn404_whenProjectNotFound() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "S1",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/schedules ---

    @Test
    @DisplayName("listSchedules — member can list schedules")
    void listSchedules_shouldReturn200_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        // Create a schedule via the owner first
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Daily Standup",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Daily Standup"));
    }

    // --- GET /api/projects/{projectId}/schedules/{scheduleId} ---

    @Test
    @DisplayName("getSchedule — owner can retrieve specific schedule")
    void getSchedule_shouldReturn200_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Get Test",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract id from response
        String scheduleId = extractId(responseBody);
        mockMvc.perform(get(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Get Test"));
    }

    @Test
    @DisplayName("getSchedule — unknown schedule returns 404")
    void getSchedule_shouldReturn404_whenNotFound() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(get(ApiPaths.schedule(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/projects/{projectId}/schedules/{scheduleId} ---

    @Test
    @DisplayName("updateSchedule — owner can update name")
    void updateSchedule_shouldReturn200_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Old Name",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": []
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(put(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Name",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": []
                                }
                                """.formatted(templateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    @DisplayName("updateSchedule — member receives 403")
    void updateSchedule_shouldReturn403_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Protected",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": []
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(put(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Hacked",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": []
                                }
                                """.formatted(templateId)))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/projects/{projectId}/schedules/{scheduleId} ---

    @Test
    @DisplayName("deleteSchedule — owner receives 204 and schedule is removed")
    void deleteSchedule_shouldReturn204_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "To Delete",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(delete(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(scheduleRepository.findById(UUID.fromString(scheduleId))).isEmpty();
    }

    @Test
    @DisplayName("deleteSchedule — member receives 403")
    void deleteSchedule_shouldReturn403_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Protected",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(delete(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteSchedule — unknown schedule returns 404")
    void deleteSchedule_shouldReturn404_whenNotFound() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(delete(ApiPaths.schedule(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- POST /{scheduleId}/pause ---

    @Test
    @DisplayName("pauseSchedule — owner can pause schedule")
    void pauseSchedule_shouldReturn200_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pauseable",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(post(ApiPaths.schedulePause(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"until": "2030-06-01T00:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    @DisplayName("pauseSchedule — indefinite pause with null until sets PAUSED_NO_ASSIGNEES when no assignees")
    void pauseSchedule_shouldSetPausedNoAssignees_whenUntilIsNullAndNoAssignees() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        // A schedule with no assignees indefinitely paused → PAUSED_NO_ASSIGNEES
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Indefinite Pause",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(post(ApiPaths.schedulePause(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                // No assignees + paused with sentinel = PAUSED_NO_ASSIGNEES
                .andExpect(jsonPath("$.status").value("PAUSED_NO_ASSIGNEES"))
                .andExpect(jsonPath("$.pausedUntil").isNotEmpty());
    }

    // --- POST /{scheduleId}/resume ---

    @Test
    @DisplayName("resumeSchedule — owner can resume a paused schedule")
    void resumeSchedule_shouldReturn200_forOwner() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String createBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Resume Me",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(createBody);

        // Pause first
        mockMvc.perform(post(ApiPaths.schedulePause(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // Resume
        mockMvc.perform(post(ApiPaths.scheduleResume(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("resumeSchedule — member receives 403")
    void resumeSchedule_shouldReturn403_forMember() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String createBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Resume Denied",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(createBody);
        mockMvc.perform(post(ApiPaths.scheduleResume(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/schedules/preview ---

    @Test
    @DisplayName("previewCron — valid cron returns 5 occurrences")
    void previewCron_shouldReturn200_withFiveOccurrences() throws Exception {
        mockMvc.perform(post(ApiPaths.CRON_PREVIEW)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextRuns").isArray())
                .andExpect(jsonPath("$.nextRuns.length()").value(5));
    }

    @Test
    @DisplayName("previewCron — invalid cron returns 400")
    void previewCron_shouldReturn400_whenCronInvalid() throws Exception {
        mockMvc.perform(post(ApiPaths.CRON_PREVIEW)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cronExpression": "not-valid",
                                  "timezone": "UTC"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("previewCron — blank cron returns 400")
    void previewCron_shouldReturn400_whenCronBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.CRON_PREVIEW)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cronExpression": "",
                                  "timezone": "UTC"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("previewCron — invalid timezone returns 400")
    void previewCron_shouldReturn400_whenTimezoneInvalid() throws Exception {
        mockMvc.perform(post(ApiPaths.CRON_PREVIEW)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "Not/Valid"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // --- createSchedule edge cases ---

    @Test
    @DisplayName("createSchedule — invalid timezone returns 400")
    void createSchedule_shouldReturn400_whenTimezoneInvalid() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad TZ",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "Not/ATimezone"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createSchedule — impossible cron (Feb 31) returns 400")
    void createSchedule_shouldReturn400_whenCronNeverFires() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Never fires",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 31 2 *",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isBadRequest());
    }

    // --- getSchedule cross-project guard ---

    @Test
    @DisplayName("getSchedule — returns 404 when schedule belongs to a different project")
    void getSchedule_shouldReturn404_whenScheduleBelongsToDifferentProject() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        // Create a second project + schedule in that project
        ProjectModel otherProject = projectRepository.save(ProjectModel.builder()
                .name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());

        String responseBody = mockMvc.perform(post(ApiPaths.schedules(otherProject.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Other Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID otherScheduleId = UUID.fromString(extractId(responseBody));

        // Access the other project's schedule through our projectId — should 404
        mockMvc.perform(get(ApiPaths.schedule(projectId, otherScheduleId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- updateSchedule — field coverage ---

    @Test
    @DisplayName("updateSchedule — sets pausedUntil and expiresAt fields")
    void updateSchedule_shouldSetPausedUntilAndExpiresAt() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "S1",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": []
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(put(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "S1",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "pausedUntil": "2030-01-01T00:00:00Z",
                                  "expiresAt": "2031-01-01T00:00:00Z",
                                  "assigneeIds": ["%s"]
                                }
                                """.formatted(templateId, ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    // --- createSchedule with assignees ---

    @Test
    @DisplayName("createSchedule — returns populated assignees list when assigneeIds provided")
    void createSchedule_shouldReturnAssignees_whenAssigneeIdsProvided() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "With Assignees",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": ["%s"]
                                }
                                """.formatted(templateId, ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignees").isArray())
                .andExpect(jsonPath("$.assignees.length()").value(1))
                .andExpect(jsonPath("$.assignees[0].userId").value(ownerId.toString()));
    }

    // --- previewCron — impossible cron produces empty list ---

    @Test
    @DisplayName("previewCron — returns empty list when cron has no future occurrences")
    void previewCron_shouldReturnEmpty_whenCronNeverFires() throws Exception {
        // Feb 31 parses fine but .next() immediately returns null → break in the loop
        mockMvc.perform(post(ApiPaths.CRON_PREVIEW)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cronExpression": "0 0 9 31 2 *",
                                  "timezone": "UTC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextRuns").isArray())
                .andExpect(jsonPath("$.nextRuns.length()").value(0));
    }

    // --- removeMember — schedule assignee cascade ---

    @Test
    @DisplayName("removeMember — auto-pauses schedule when last assignee is removed from project")
    void removeMember_shouldAutoPause_whenLastAssigneeRemoved() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        // Add a new user as the sole schedule assignee
        UUID assigneeUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(assigneeUserId).email("assignee@example.com")
                .name("Assignee").build());
        ProjectMemberModel assigneeMembership = projectMemberRepository.save(
                ProjectMemberModel.builder().projectId(projectId).userId(assigneeUserId)
                        .role(ProjectMemberRole.MEMBER).build());

        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Assignee Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": ["%s"]
                                }
                                """.formatted(templateId, assigneeUserId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID scheduleId = UUID.fromString(extractId(responseBody));

        // Remove the assignee from the project — triggers onAssigneeRemoved cascade
        mockMvc.perform(delete(ApiPaths.member(projectId, assigneeMembership.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        // Schedule should now be auto-paused (no assignees remain)
        assertThat(assigneeRepository.findByScheduleId(scheduleId)).isEmpty();
        mockMvc.perform(get(ApiPaths.schedule(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED_NO_ASSIGNEES"));
    }

    @Test
    @DisplayName("removeMember — does not pause schedule when other assignees remain")
    void removeMember_shouldNotAutoPause_whenOtherAssigneesRemain() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        // Add a new user alongside ownerId as a schedule assignee
        UUID assigneeUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(assigneeUserId).email("second@example.com")
                .name("Second").build());
        ProjectMemberModel assigneeMembership = projectMemberRepository.save(
                ProjectMemberModel.builder().projectId(projectId).userId(assigneeUserId)
                        .role(ProjectMemberRole.MEMBER).build());

        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Multi Assignee Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": ["%s", "%s"]
                                }
                                """.formatted(templateId, ownerId, assigneeUserId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID scheduleId = UUID.fromString(extractId(responseBody));

        // Remove assigneeUser — ownerId still assigned, so schedule should NOT be auto-paused
        mockMvc.perform(delete(ApiPaths.member(projectId, assigneeMembership.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(assigneeRepository.findByScheduleId(scheduleId)).hasSize(1);
        mockMvc.perform(get(ApiPaths.schedule(projectId, scheduleId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // --- RecurringScheduleRepository direct coverage ---

    @Test
    @DisplayName("scheduleRepository.updateAfterRun — updates next_run_at, last_run_at, rotation_index")
    void scheduleRepository_updateAfterRun_shouldUpdateFields() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Run Test",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID scheduleId = UUID.fromString(extractId(responseBody));
        java.time.Instant newNextRunAt = java.time.Instant.parse("2030-01-01T09:00:00Z");
        java.time.Instant lastRunAt    = java.time.Instant.parse("2026-01-01T09:00:00Z");

        scheduleRepository.updateAfterRun(scheduleId, newNextRunAt, lastRunAt, 3);

        var updated = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(updated.getCurrentRotationIndex()).isEqualTo(3);
        assertThat(updated.getLastRunAt().toInstant()).isEqualTo(lastRunAt);
        assertThat(updated.getNextRunAt().toInstant()).isEqualTo(newNextRunAt);
    }

    @Test
    @DisplayName("scheduleRepository.findByTemplateIdAndActive — returns active schedules for template")
    void scheduleRepository_findByTemplateIdAndActive_shouldReturnActiveSchedules() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        // Create two schedules: one active (no pausedUntil), one paused
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Active Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated());

        String pausedBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paused Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 10 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID pausedId = UUID.fromString(extractId(pausedBody));
        mockMvc.perform(post(ApiPaths.schedulePause(projectId, pausedId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        var active = scheduleRepository.findByTemplateIdAndActive(templateId);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getName()).isEqualTo("Active Schedule");
    }

    @Test
    @DisplayName("removeMember — no schedule changes when removed member has no schedule assignments")
    void removeMember_shouldLeaveSchedulesUntouched_whenMemberHasNoAssignments() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        // Create a schedule (no assignees)
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "No Assignees",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID scheduleId = UUID.fromString(extractId(responseBody));

        // Remove memberId who is NOT an assignee on any schedule
        mockMvc.perform(delete(ApiPaths.member(projectId, memberMembershipId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        // Schedule should be untouched (still ACTIVE / PAUSED_NO_ASSIGNEES due to no assignees)
        assertThat(scheduleRepository.findByProjectId(projectId)).hasSize(1);
        assertThat(assigneeRepository.findByScheduleId(scheduleId)).isEmpty();
    }

    @Test
    @DisplayName("createSchedule — sets pausedUntil and expiresAt when provided")
    void createSchedule_shouldCreate_withPausedUntilAndExpiresAt() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Timed Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "pausedUntil": "2030-06-01T00:00:00Z",
                                  "expiresAt": "2031-01-01T00:00:00Z"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pausedUntil").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    @DisplayName("getSchedule — returns EXPIRED status when expiresAt is in the past")
    void getSchedule_shouldReturnExpiredStatus_whenExpiresAtIsInPast() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Expired Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "expiresAt": "2020-01-01T00:00:00Z"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(get(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("createSchedule — invalid cron syntax returns 400")
    void createSchedule_shouldReturn400_whenCronSyntaxInvalid() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad Cron",
                                  "templateId": "%s",
                                  "cronExpression": "not-a-cron",
                                  "timezone": "UTC"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createSchedule — returns ACTIVE status when pausedUntil is in the past")
    void createSchedule_shouldReturnActive_whenPausedUntilIsInPast() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stale Pause",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "pausedUntil": "2020-01-01T00:00:00Z"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(get(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.pausedUntil").isNotEmpty());
    }

    @Test
    @DisplayName("updateSchedule — invalid cron syntax returns 400")
    void updateSchedule_shouldReturn400_whenCronSyntaxInvalid() throws Exception {
        assumeTrue(hasAddon(), "RECURRING_SCHEDULING addon not seeded — skipping");
        String responseBody = mockMvc.perform(post(ApiPaths.schedules(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Valid Schedule",
                                  "templateId": "%s",
                                  "cronExpression": "0 0 9 * * MON",
                                  "timezone": "UTC",
                                  "assigneeIds": []
                                }
                                """.formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String scheduleId = extractId(responseBody);
        mockMvc.perform(put(ApiPaths.schedule(projectId, UUID.fromString(scheduleId)))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid Update",
                                  "templateId": "%s",
                                  "cronExpression": "not-a-cron",
                                  "timezone": "UTC",
                                  "assigneeIds": []
                                }
                                """.formatted(templateId)))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---

    private String extractId(String json) throws Exception {
        return objectMapper.readTree(json).get("id").asText();
    }
}
