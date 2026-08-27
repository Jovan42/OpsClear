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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("create_shouldReturn409_whenCallerAlreadyBelongsToAnOrganisation")
    void create_shouldReturn409_whenCallerAlreadyBelongsToAnOrganisation() throws Exception {
        // JOB-241: confirms the actual enforced behavior per ADR-0049 Appendix §2 —
        // previously this succeeded with 201, leaving the caller a member of two orgs.
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Second Corp", "slug", "SEC"))))
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
    @DisplayName("create_shouldReturn201_whenSlugBelongedToASoftDeletedOrganisation")
    void create_shouldReturn201_whenSlugBelongedToASoftDeletedOrganisation() throws Exception {
        // JOB-238: a soft-deleted org's slug must actually be reusable, not just look
        // free per requireSlugAvailable()'s non-deleted-only check and then 500 when the
        // INSERT hits a DB constraint that didn't know about the soft-delete exclusion.
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

        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("ACM"));
    }

    @Test
    @DisplayName("create_shouldReturn409NotRawServerError_whenTwoConcurrentRequestsRaceForTheSameNeverUsedSlug")
    void create_shouldReturn409NotRawServerError_whenTwoConcurrentRequestsRaceForTheSameNeverUsedSlug() throws Exception {
        // JOB-238: requireSlugAvailable() passing is check-then-act, not a lock — two
        // requests for the same slug that both pass it before either commits must still
        // resolve to a clean 409 for the loser, not an unhandled 500. Fires two genuinely
        // concurrent real HTTP requests (via mockMvc, real Testcontainers Postgres) synced
        // on a CyclicBarrier so both threads submit their INSERT at the same instant,
        // rather than mocking the repository the way the unit test does.
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> createOrgRacing(ownerId, "owner@example.com", "Owner", barrier));
            Future<Integer> second = executor.submit(() -> createOrgRacing(memberId, "member@example.com", "Member", barrier));

            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    private int createOrgRacing(UUID callerId, String email, String name, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", email).claim("name", name)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Race Corp", "slug", "RCE"))))
                .andReturn().getResponse().getStatus();
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
    @DisplayName("update_shouldReturn409NotRawServerError_whenTwoConcurrentRequestsRaceForTheSameNeverUsedSlug")
    void update_shouldReturn409NotRawServerError_whenTwoConcurrentRequestsRaceForTheSameNeverUsedSlug() throws Exception {
        // JOB-238: same race as create's equivalent test, but for update — two different
        // orgs' owners simultaneously renaming their own org's slug to the same
        // never-before-used value.
        UUID firstOwnerId = UUID.randomUUID();
        UUID secondOwnerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(firstOwnerId).email("first@example.com").name("First").build());
        userRepository.save(UserModel.builder().id(secondOwnerId).email("second@example.com").name("Second").build());

        UUID firstOrgId = createOrgAs(firstOwnerId, "first@example.com", "First", "FST");
        UUID secondOrgId = createOrgAs(secondOwnerId, "second@example.com", "Second", "SND");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() ->
                    updateOrgSlugRacing(firstOrgId, firstOwnerId, "first@example.com", "First", barrier));
            Future<Integer> second = executor.submit(() ->
                    updateOrgSlugRacing(secondOrgId, secondOwnerId, "second@example.com", "Second", barrier));

            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID createOrgAs(UUID callerId, String email, String name, String slug) throws Exception {
        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", email).claim("name", name)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", slug + " Corp", "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private int updateOrgSlugRacing(UUID orgId, UUID callerId, String email, String name, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(patch(ApiPaths.organisation(orgId))
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", email).claim("name", name)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Race Corp", "slug", "RCX"))))
                .andReturn().getResponse().getStatus();
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
