package com.opsclear.integration;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import com.opsclear.repository.UserRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class ProjectMemberIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockReasonRepository blockReasonRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobStatusHistoryRepository jobStatusHistoryRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleAssigneeRepository scheduleAssigneeRepository;

    @Autowired
    private RecurringScheduleRepository scheduleRepository;

    @Autowired
    private JobTemplateRepository jobTemplateRepository;

    private UUID ownerId;
    private UUID project1Id;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        scheduleAssigneeRepository.deleteAll();
        scheduleRepository.deleteAll();
        jobTemplateRepository.deleteAll();
        projectMemberRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(ownerId).email("owner@example.com").name("Owner User").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);

        ProjectModel project = projectRepository.save(ProjectModel.builder()
                .name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        project1Id = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(ownerId).role(ProjectMemberRole.OWNER).build());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(
            UUID userId, String email, String name) {
        return jwt().jwt(j -> j.subject(userId.toString())
                .claim("email", email)
                .claim("name", name));
    }

    // ─── Add member ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWNER can add a MEMBER to the project")
    void addMember_shouldReturn201_whenOwnerAddsUser() throws Exception {
        UUID newUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(newUserId).email("new@example.com").name("New User").build());

        String body = """
                { "userId": "%s", "role": "MEMBER" }
                """.formatted(newUserId);

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(newUserId.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.projectId").value(project1Id.toString()));

        assertThat(projectMemberRepository.existsByProjectIdAndUserId(project1Id, newUserId)).isTrue();
    }

    @Test
    @DisplayName("ADMIN can add a MEMBER to the project")
    void addMember_shouldReturn201_whenAdminAddsUser() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(adminId).email("admin@example.com").name("Admin User").build());
        userRepository.save(UserModel.builder()
                .id(newUserId).email("new@example.com").name("New User").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(adminId).role(ProjectMemberRole.ADMIN).build());

        String body = """
                { "userId": "%s", "role": "MEMBER" }
                """.formatted(newUserId);

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .with(jwtFor(adminId, "admin@example.com", "Admin User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    @DisplayName("MEMBER cannot add users to the project")
    void addMember_shouldReturn403_whenMemberRole() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("member@example.com").name("Member User").build());
        userRepository.save(UserModel.builder()
                .id(newUserId).email("new@example.com").name("New User").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        String body = """
                { "userId": "%s", "role": "MEMBER" }
                """.formatted(newUserId);

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .with(jwtFor(memberId, "member@example.com", "Member User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Non-member cannot add users to the project")
    void addMember_shouldReturn403_whenNotMember() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(outsiderId).email("outsider@example.com").name("Outsider").build());
        userRepository.save(UserModel.builder()
                .id(newUserId).email("new@example.com").name("New User").build());

        String body = """
                { "userId": "%s", "role": "MEMBER" }
                """.formatted(newUserId);

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .with(jwtFor(outsiderId, "outsider@example.com", "Outsider"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cannot assign OWNER role when adding a member")
    void addMember_shouldReturn403_whenAssigningOwnerRole() throws Exception {
        UUID newUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(newUserId).email("new@example.com").name("New User").build());

        String body = """
                { "userId": "%s", "role": "OWNER" }
                """.formatted(newUserId);

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot assign OWNER role"));
    }

    @Test
    @DisplayName("Returns 409 when adding a user already in the project")
    void addMember_shouldReturn409_whenAlreadyMember() throws Exception {
        String body = """
                { "userId": "%s", "role": "MEMBER" }
                """.formatted(ownerId);

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Returns 404 when adding a non-existent user")
    void addMember_shouldReturn404_whenUserNotFound() throws Exception {
        String body = """
                { "userId": "%s", "role": "MEMBER" }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Returns 401 without authentication")
    void addMember_shouldReturn401_withoutAuth() throws Exception {
        String body = """
                { "userId": "%s", "role": "MEMBER" }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ─── List members ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Member can list all project members")
    void listMembers_shouldReturn200_withMemberDetails() throws Exception {
        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("member@example.com").name("Regular Member").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        mockMvc.perform(get(ApiPaths.members(project1Id))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").exists())
                .andExpect(jsonPath("$[0].userId").exists())
                .andExpect(jsonPath("$[0].userName").exists());
    }

    @Test
    @DisplayName("Non-member cannot list project members")
    void listMembers_shouldReturn403_whenNotMember() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(outsiderId).email("out@example.com").name("Outsider").build());

        mockMvc.perform(get(ApiPaths.members(project1Id))
                        .with(jwtFor(outsiderId, "out@example.com", "Outsider")))
                .andExpect(status().isForbidden());
    }

    // ─── Update role ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWNER can promote a MEMBER to ADMIN")
    void updateRole_shouldReturn200_whenOwnerPromotesMember() throws Exception {
        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("member@example.com").name("Member").build());
        ProjectMemberModel membership = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        String body = """
                { "role": "ADMIN" }
                """;

        mockMvc.perform(put(ApiPaths.member(project1Id, membership.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("MEMBER cannot change roles")
    void updateRole_shouldReturn403_whenMemberRole() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("m1@example.com").name("Member 1").build());
        userRepository.save(UserModel.builder()
                .id(otherMemberId).email("m2@example.com").name("Member 2").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());
        ProjectMemberModel target = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(otherMemberId).role(ProjectMemberRole.MEMBER).build());

        String body = """
                { "role": "ADMIN" }
                """;

        mockMvc.perform(put(ApiPaths.member(project1Id, target.getId()))
                        .with(jwtFor(memberId, "m1@example.com", "Member 1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cannot assign OWNER role via update")
    void updateRole_shouldReturn403_whenAssigningOwnerRole() throws Exception {
        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("m@example.com").name("Member").build());
        ProjectMemberModel membership = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        String body = """
                { "role": "OWNER" }
                """;

        mockMvc.perform(put(ApiPaths.member(project1Id, membership.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cannot change the project owner's role")
    void updateRole_shouldReturn403_whenChangingOwnerRole() throws Exception {
        ProjectMemberModel ownerMembership = projectMemberRepository
                .findByProjectIdAndUserId(project1Id, ownerId).orElseThrow();

        String body = """
                { "role": "ADMIN" }
                """;

        mockMvc.perform(put(ApiPaths.member(project1Id, ownerMembership.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot change the project owner's role"));
    }

    // ─── Remove member ────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWNER can remove a MEMBER")
    void removeMember_shouldReturn204_whenOwnerRemovesMember() throws Exception {
        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("m@example.com").name("Member").build());
        ProjectMemberModel membership = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        mockMvc.perform(delete(ApiPaths.member(project1Id, membership.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isNoContent());

        assertThat(projectMemberRepository.findById(membership.getId())).isEmpty();
    }

    @Test
    @DisplayName("MEMBER cannot remove other members")
    void removeMember_shouldReturn403_whenMemberRole() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("m1@example.com").name("Member 1").build());
        userRepository.save(UserModel.builder()
                .id(otherMemberId).email("m2@example.com").name("Member 2").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());
        ProjectMemberModel target = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(otherMemberId).role(ProjectMemberRole.MEMBER).build());

        mockMvc.perform(delete(ApiPaths.member(project1Id, target.getId()))
                        .with(jwtFor(memberId, "m1@example.com", "Member 1")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cannot remove the project owner")
    void removeMember_shouldReturn403_whenRemovingOwner() throws Exception {
        ProjectMemberModel ownerMembership = projectMemberRepository
                .findByProjectIdAndUserId(project1Id, ownerId).orElseThrow();

        mockMvc.perform(delete(ApiPaths.member(project1Id, ownerMembership.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot remove the project owner"));
    }

    @Test
    @DisplayName("Returns 404 when removing a non-existent member")
    void removeMember_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.member(project1Id, UUID.randomUUID()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Returns 404 when updating a membership that belongs to a different project")
    void updateRole_shouldReturn404_whenMemberFromDifferentProject() throws Exception {
        ProjectModel project2 = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project2.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());

        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("m@example.com").name("Member").build());
        ProjectMemberModel membershipInProject2 = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project2.getId()).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        String body = """
                { "role": "ADMIN" }
                """;

        mockMvc.perform(put(ApiPaths.member(project1Id, membershipInProject2.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Returns 404 when removing a membership that belongs to a different project")
    void removeMember_shouldReturn404_whenMemberFromDifferentProject() throws Exception {
        ProjectModel project2 = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project2.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());

        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("m@example.com").name("Member").build());
        ProjectMemberModel membershipInProject2 = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project2.getId()).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        mockMvc.perform(delete(ApiPaths.member(project1Id, membershipInProject2.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isNotFound());
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("addMember_shouldReturn400WithMultipleMessages_whenBothFieldsMissing")
    void addMember_shouldReturn400WithMultipleMessages_whenBothFieldsMissing() throws Exception {
        // Empty body: both userId and role are @NotNull, producing two field errors
        // simultaneously and exercising the reduce((a, b) -> a + "; " + b) branch
        // in handleValidation.
        mockMvc.perform(post(ApiPaths.members(project1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(";")));
    }

    @Test
    @DisplayName("Returns 404 when project does not exist")
    void listMembers_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.members(UUID.randomUUID()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("removeMember — auto-pauses schedule when removed member was its only assignee")
    void removeMember_shouldAutoPauseSchedule_whenMemberWasScheduleAssignee() throws Exception {
        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("member@example.com").name("Member User").build());
        ProjectMemberModel membership = projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project1Id).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(project1Id).name("Test Template")
                .assigneeMode("NONE").createdBy(ownerId).build());

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(project1Id);
        row.setTemplateId(template.getId());
        row.setName("Test Schedule");
        row.setCronExpression("0 0 9 * * MON");
        row.setTimezone("UTC");
        row.setNextRunAt(Instant.now().plusSeconds(3600).atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        RecurringSchedulesRecord savedSchedule = scheduleRepository.insert(row);
        scheduleAssigneeRepository.replaceForSchedule(savedSchedule.getId(), List.of(memberId));

        mockMvc.perform(delete(ApiPaths.member(project1Id, membership.getId()))
                        .with(jwtFor(ownerId, "owner@example.com", "Owner User")))
                .andExpect(status().isNoContent());

        RecurringSchedulesRecord updated = scheduleRepository.findById(savedSchedule.getId()).orElseThrow();
        assertThat(updated.getPausedUntil()).isNotNull();
        assertThat(updated.getPausedUntil().toInstant()).isEqualTo(Instant.parse("9999-01-01T00:00:00Z"));
    }
}
