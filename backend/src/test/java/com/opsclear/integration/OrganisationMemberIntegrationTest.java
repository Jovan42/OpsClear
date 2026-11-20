package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Organisation member endpoint")
class OrganisationMemberIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgInviteRepository orgInviteRepository;
    @Autowired private NotificationRepository notificationRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID orgId;

    // Accepting an invite creates a notification (JOB-279): leave none behind, or they block
    // other classes from deleting organisations, projects and users.
    @AfterEach
    void deleteNotifications() {
        notificationRepository.deleteAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        orgInviteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());

        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        orgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // ─── listMembers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listMembers_shouldReturn200_withOwnerInList")
    void listMembers_shouldReturn200_withOwnerInList() throws Exception {
        mockMvc.perform(get(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(ownerId.toString()))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    @DisplayName("listMembers_shouldReturn403_whenCallerIsNotMember")
    void listMembers_shouldReturn403_whenCallerIsNotMember() throws Exception {
        mockMvc.perform(get(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isForbidden());
    }

    // ─── direct add (removed, JOB-402) ───────────────────────────────────────

    @Test
    @DisplayName("directAdd_shouldReturn405AndNotJoin_whenPostedToMembers")
    void directAdd_shouldReturn405AndNotJoin_whenPostedToMembers() throws Exception {
        mockMvc.perform(post(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userId", memberId.toString(), "role", "MEMBER"))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.message").value("Method not supported for this endpoint"));

        assertThat(organisationRepository.existsMember(orgId, memberId)).as("joined by direct add").isFalse();
    }

    // ─── invite accept (the only way in) ──────────────────────────────────────

    @Test
    @DisplayName("inviteAccept_shouldCreateMembership_withMemberRole")
    void inviteAccept_shouldCreateMembership_withMemberRole() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(createResponse).get("token").asText();

        mockMvc.perform(post(ApiPaths.acceptInvite(token))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isOk());

        assertThat(organisationRepository.findMemberRole(orgId, memberId)).contains(OrganisationRole.MEMBER);
        mockMvc.perform(get(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.userId == '" + memberId + "')].role").value("MEMBER"));
    }

    // ─── updateRole ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateRole_shouldReturn200_whenOwnerChangesRole")
    void updateRole_shouldReturn200_whenOwnerChangesRole() throws Exception {
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);

        mockMvc.perform(patch(ApiPaths.orgMember(orgId, memberId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.userId").value(memberId.toString()));
    }

    @Test
    @DisplayName("updateRole_shouldReturn403_whenCallerIsNotOwner")
    void updateRole_shouldReturn403_whenCallerIsNotOwner() throws Exception {
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.ADMIN);

        mockMvc.perform(patch(ApiPaths.orgMember(orgId, outsiderId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateRole_shouldReturn403_whenChangingOwnerRole")
    void updateRole_shouldReturn403_whenChangingOwnerRole() throws Exception {
        mockMvc.perform(patch(ApiPaths.orgMember(orgId, ownerId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateRole_shouldReturn403_whenAssigningOwnerRole")
    void updateRole_shouldReturn403_whenAssigningOwnerRole() throws Exception {
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);

        mockMvc.perform(patch(ApiPaths.orgMember(orgId, memberId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateRole_shouldReturn404_whenTargetMemberNotFound")
    void updateRole_shouldReturn404_whenTargetMemberNotFound() throws Exception {
        mockMvc.perform(patch(ApiPaths.orgMember(orgId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isNotFound());
    }

    // ─── removeMember ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeMember_shouldReturn204_whenOwnerRemoves")
    void removeMember_shouldReturn204_whenOwnerRemoves() throws Exception {
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);

        mockMvc.perform(delete(ApiPaths.orgMember(orgId, memberId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("removeMember_shouldReturn204_whenAdminRemoves")
    void removeMember_shouldReturn204_whenAdminRemoves() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(adminId).email("admin@example.com").name("Admin").build());
        userRepository.save(UserModel.builder().id(targetId).email("target@example.com").name("Target").build());
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.ADMIN);
        organisationRepository.saveMember(orgId, targetId, OrganisationRole.MEMBER);

        mockMvc.perform(delete(ApiPaths.orgMember(orgId, targetId))
                        .with(jwt().jwt(j -> j.subject(adminId.toString())
                                .claim("email", "admin@example.com").claim("name", "Admin"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("removeMember_shouldReturn403_whenCallerIsMember")
    void removeMember_shouldReturn403_whenCallerIsMember() throws Exception {
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);

        mockMvc.perform(delete(ApiPaths.orgMember(orgId, ownerId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("removeMember_shouldReturn403_whenRemovingOwner")
    void removeMember_shouldReturn403_whenRemovingOwner() throws Exception {
        mockMvc.perform(delete(ApiPaths.orgMember(orgId, ownerId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("removeMember_shouldReturn404_whenTargetMemberNotFound")
    void removeMember_shouldReturn404_whenTargetMemberNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.orgMember(orgId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }
}
