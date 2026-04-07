package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrgInviteRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Org invite endpoint")
class OrgInviteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrgInviteRepository orgInviteRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID orgId;

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
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(adminId).email("admin@example.com").name("Admin").build());
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

        organisationRepository.saveMember(orgId, adminId, OrganisationRole.ADMIN);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
    }

    // ─── sendInvite ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendInvite_shouldReturn201_whenOwnerInvitesNewEmail")
    void sendInvite_shouldReturn201_whenOwnerInvitesNewEmail() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.organisationId").value(orgId.toString()))
                .andExpect(jsonPath("$.invitedByName").value("Owner"));
    }

    @Test
    @DisplayName("sendInvite_shouldReturn201_whenAdminInvitesNewEmail")
    void sendInvite_shouldReturn201_whenAdminInvitesNewEmail() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(adminId.toString())
                                .claim("email", "admin@example.com").claim("name", "Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    @DisplayName("sendInvite_shouldReturn403_whenCallerIsMember")
    void sendInvite_shouldReturn403_whenCallerIsMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn409_whenEmailAlreadyMember")
    void sendInvite_shouldReturn409_whenEmailAlreadyMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn409_whenPendingInviteAlreadyExists")
    void sendInvite_shouldReturn409_whenPendingInviteAlreadyExists() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn400_whenEmailIsBlank")
    void sendInvite_shouldReturn400_whenEmailIsBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", ""))))
                .andExpect(status().isBadRequest());
    }

    // ─── listInvites ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listInvites_shouldReturnPendingInvites_whenCallerIsOwner")
    void listInvites_shouldReturnPendingInvites_whenCallerIsOwner() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "invite1@example.com"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("invite1@example.com"));
    }

    @Test
    @DisplayName("listInvites_shouldReturn403_whenCallerIsMember")
    void listInvites_shouldReturn403_whenCallerIsMember() throws Exception {
        mockMvc.perform(get(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isForbidden());
    }

    // ─── revokeInvite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeInvite_shouldReturn204_whenOwnerRevokes")
    void revokeInvite_shouldReturn204_whenOwnerRevokes() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "invited@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID inviteId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("revokeInvite_shouldReturn400_whenInviteAlreadyRevoked")
    void revokeInvite_shouldReturn400_whenInviteAlreadyRevoked() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "invited@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID inviteId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("revokeInvite_shouldReturn404_whenInviteNotFound")
    void revokeInvite_shouldReturn404_whenInviteNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }

    // ─── acceptInvite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptInvite_shouldReturn200_whenTokenValidAndEmailMatches")
    void acceptInvite_shouldReturn200_whenTokenValidAndEmailMatches() throws Exception {
        UUID newUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(newUserId).email("newuser@example.com").name("New User").build());

        String createResponse = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(createResponse).get("token") != null
                ? objectMapper.readTree(createResponse).get("token").asText()
                : orgInviteRepository.findPendingByOrgId(orgId).get(0).getToken();

        mockMvc.perform(post(ApiPaths.acceptInvite(token))
                        .with(jwt().jwt(j -> j.subject(newUserId.toString())
                                .claim("email", "newuser@example.com").claim("name", "New User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    @DisplayName("acceptInvite_shouldReturn403_whenEmailDoesNotMatch")
    void acceptInvite_shouldReturn403_whenEmailDoesNotMatch() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "specific@example.com"))))
                .andExpect(status().isCreated());

        String token = orgInviteRepository.findPendingByOrgId(orgId).get(0).getToken();

        mockMvc.perform(post(ApiPaths.acceptInvite(token))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("acceptInvite_shouldReturn404_whenTokenDoesNotExist")
    void acceptInvite_shouldReturn404_whenTokenDoesNotExist() throws Exception {
        mockMvc.perform(post(ApiPaths.acceptInvite("nonexistent-token"))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("acceptInvite_shouldReturn409_whenUserAlreadyMember")
    void acceptInvite_shouldReturn409_whenUserAlreadyMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "outsider@example.com"))))
                .andExpect(status().isCreated());

        String token = orgInviteRepository.findPendingByOrgId(orgId).get(0).getToken();

        organisationRepository.saveMember(orgId, outsiderId, OrganisationRole.MEMBER);

        mockMvc.perform(post(ApiPaths.acceptInvite(token))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isConflict());
    }
}
