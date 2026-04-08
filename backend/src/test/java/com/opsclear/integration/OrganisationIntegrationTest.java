package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Organisation endpoint")
class OrganisationIntegrationTest {

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

    private UUID ownerId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
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

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
    }

    @Test
    @DisplayName("create_shouldReturn201_withOrgDetails")
    void create_shouldReturn201_withOrgDetails() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.slug").value("ACM"))
                .andExpect(jsonPath("$.createdBy").value(ownerId.toString()))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("create_shouldNormaliseSlugToUppercase")
    void create_shouldNormaliseSlugToUppercase() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "acm"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("ACM"));
    }

    @Test
    @DisplayName("create_shouldReturn409_whenSlugAlreadyExists")
    void create_shouldReturn409_whenSlugAlreadyExists() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated());

        UUID other = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(other).email("other@example.com").name("Other").build());

        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(other.toString())
                                .claim("email", "other@example.com").claim("name", "Other")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Another Corp", "slug", "ACM"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("create_shouldReturn400_whenSlugTooLong")
    void create_shouldReturn400_whenSlugTooLong() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "TOOLONG"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("getById_shouldReturnOrg_forMember")
    void getById_shouldReturnOrg_forMember() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(get(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("ACM"));
    }

    @Test
    @DisplayName("getById_shouldReturn403_whenCallerIsNotMember")
    void getById_shouldReturn403_whenCallerIsNotMember() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(get(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getById_shouldReturn404_whenOrgDoesNotExist")
    void getById_shouldReturn404_whenOrgDoesNotExist() throws Exception {
        mockMvc.perform(get(ApiPaths.organisation(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("update_shouldUpdateNameAndSlug_whenCallerIsOwner")
    void update_shouldUpdateNameAndSlug_whenCallerIsOwner() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(patch(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated Corp", "slug", "UPD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Corp"))
                .andExpect(jsonPath("$.slug").value("UPD"));
    }

    @Test
    @DisplayName("update_shouldReturn403_whenCallerIsNotOwner")
    void update_shouldReturn403_whenCallerIsNotOwner() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(patch(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hack", "slug", "HCK"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("delete_shouldReturn204_whenCallerIsOwner")
    void delete_shouldReturn204_whenCallerIsOwner() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(delete(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("delete_shouldReturn403_whenCallerIsNotOwner")
    void delete_shouldReturn403_whenCallerIsNotOwner() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(delete(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("update_shouldReturn403_whenCallerIsAdminNotOwner")
    void update_shouldReturn403_whenCallerIsAdminNotOwner() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());
        organisationRepository.saveMember(orgId, memberId, com.opsclear.model.OrganisationRole.ADMIN);

        mockMvc.perform(patch(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hack", "slug", "HCK"))))
                .andExpect(status().isForbidden());
    }

    // --- getMyOrganisation ---

    @Test
    @DisplayName("getMyOrg_shouldReturn200_whenCallerIsMember")
    void getMyOrg_shouldReturn200_whenCallerIsMember() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.MY_ORG)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("ACM"));
    }

    @Test
    @DisplayName("getMyOrg_shouldReturn204_whenCallerHasNoOrg")
    void getMyOrg_shouldReturn204_whenCallerHasNoOrg() throws Exception {
        mockMvc.perform(get(ApiPaths.MY_ORG)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("delete_shouldReturn403_whenCallerIsAdminNotOwner")
    void delete_shouldReturn403_whenCallerIsAdminNotOwner() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());
        organisationRepository.saveMember(orgId, memberId, com.opsclear.model.OrganisationRole.ADMIN);

        mockMvc.perform(delete(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("update_shouldReturn409_whenSlugTakenByAnotherOrg")
    void update_shouldReturn409_whenSlugTakenByAnotherOrg() throws Exception {
        UUID otherId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(otherId).email("other@example.com").name("Other").build());

        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(otherId.toString())
                                .claim("email", "other@example.com").claim("name", "Other")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Other Corp", "slug", "OTH"))))
                .andExpect(status().isCreated());

        String createResponse = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orgId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(patch(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "OTH"))))
                .andExpect(status().isConflict());
    }
}
