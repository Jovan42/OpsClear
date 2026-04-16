package com.opsclear.integration;

import com.opsclear.model.MilestoneModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
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
class MilestoneIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private com.opsclear.repository.FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId  = UUID.randomUUID();
        memberId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());
    }

    // --- POST /api/projects/{projectId}/milestones ---

    @Test
    @DisplayName("OWNER should create a milestone and receive 201")
    void createMilestone_shouldReturn201_forOwner() throws Exception {
        mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sprint 1", "description": "First sprint", "deadline": "2026-04-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sprint 1"))
                .andExpect(jsonPath("$.description").value("First sprint"))
                .andExpect(jsonPath("$.deadline").value("2026-04-01"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.friendlyId").value("MIL-001"));
    }

    @Test
    @DisplayName("Should return 403 when requester is not a member on create")
    void createMilestone_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sprint X"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEMBER should be forbidden from creating a milestone")
    void createMilestone_shouldReturn403_forMember() throws Exception {
        mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sprint 1"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 400 when milestone name is blank")
    void createMilestone_shouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist on create")
    void createMilestone_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.milestones(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sprint 1"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/milestones ---

    @Test
    @DisplayName("Any member should list milestones")
    void listMilestones_shouldReturn200_forMember() throws Exception {
        milestoneRepository.save(MilestoneModel.builder().projectId(projectId).name("Alpha").build());
        milestoneRepository.save(MilestoneModel.builder().projectId(projectId).name("Beta").build());

        mockMvc.perform(get(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Should return 403 when requester is not a member on list")
    void listMilestones_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(get(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("email", "stranger@example.com"))))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/projects/{projectId}/milestones/{milestoneId} ---

    @Test
    @DisplayName("OWNER should update a milestone")
    void updateMilestone_shouldReturn200_forOwner() throws Exception {
        MilestoneModel milestone = milestoneRepository.save(
                MilestoneModel.builder().projectId(projectId).name("Old Name").build());

        mockMvc.perform(put(ApiPaths.milestone(projectId, milestone.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Name", "description": "Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    @Test
    @DisplayName("Should return 404 when milestone belongs to a different project on update")
    void updateMilestone_shouldReturn404_whenMilestoneInDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        MilestoneModel otherMilestone = milestoneRepository.save(
                MilestoneModel.builder().projectId(otherProject.getId()).name("Other Milestone").build());

        mockMvc.perform(put(ApiPaths.milestone(projectId, otherMilestone.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/projects/{projectId}/milestones/{milestoneId} ---

    @Test
    @DisplayName("OWNER should soft delete a milestone and receive 204")
    void deleteMilestone_shouldReturn204_forOwner() throws Exception {
        MilestoneModel milestone = milestoneRepository.save(
                MilestoneModel.builder().projectId(projectId).name("To Delete").build());

        assertThat(milestone.isDeleted()).isFalse();

        mockMvc.perform(delete(ApiPaths.milestone(projectId, milestone.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        // Exercise MilestoneModel.softDelete() and isDeleted() true branch
        milestone.softDelete();
        assertThat(milestone.isDeleted()).isTrue();

        // Verify soft delete — milestone is no longer accessible
        mockMvc.perform(get(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from deleting a milestone")
    void deleteMilestone_shouldReturn403_forMember() throws Exception {
        MilestoneModel milestone = milestoneRepository.save(
                MilestoneModel.builder().projectId(projectId).name("Protected").build());

        mockMvc.perform(delete(ApiPaths.milestone(projectId, milestone.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when milestone does not exist on delete")
    void deleteMilestone_shouldReturn404_whenMilestoneNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.milestone(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("OWNER without org membership should create a milestone with null friendlyId")
    void createMilestone_shouldReturn201_withNullFriendlyId_whenOwnerHasNoOrg() throws Exception {
        UUID noOrgUserId = UUID.randomUUID();
        userRepository.save(com.opsclear.model.UserModel.builder()
                .id(noOrgUserId).email("noorg@example.com").name("NoOrg").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(noOrgUserId).role(ProjectMemberRole.OWNER).build());

        mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(j -> j.subject(noOrgUserId.toString()).claim("email", "noorg@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "No-Org Sprint"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("No-Org Sprint"))
                .andExpect(jsonPath("$.friendlyId").isEmpty());
    }

    @Test
    @DisplayName("Should return 404 when milestone belongs to a different project on delete")
    void deleteMilestone_shouldReturn404_whenMilestoneBelongsToDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        MilestoneModel otherMilestone = milestoneRepository.save(
                MilestoneModel.builder().projectId(otherProject.getId()).name("Other Milestone").build());

        mockMvc.perform(delete(ApiPaths.milestone(projectId, otherMilestone.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }
}
