package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Organisation subscription endpoint")
class SubscriptionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID orgId;
    private UUID tierId;
    private UUID addonId;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionRepository.deleteAll();
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

        mockMvc.perform(post(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", memberId, "role", "MEMBER"))))
                .andExpect(status().isCreated());

        tierId = tierRepository.findAll().get(0).getId();
        addonId = addonRepository.findAll().stream()
                .filter(a -> a.isAvailable())
                .findFirst()
                .orElseThrow()
                .getId();
    }

    // ─── GET /api/organisations/{orgId}/subscription ──────────────────────────

    @Test
    @DisplayName("getSubscription_shouldReturn404_whenNoSubscriptionExists")
    void getSubscription_shouldReturn404_whenNoSubscriptionExists() throws Exception {
        mockMvc.perform(get(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getSubscription_shouldReturn404_whenCallerIsNotMember")
    void getSubscription_shouldReturn404_whenCallerIsNotMember() throws Exception {
        mockMvc.perform(get(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getSubscription_shouldReturn401_whenUnauthenticated")
    void getSubscription_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get(ApiPaths.orgSubscription(orgId)))
                .andExpect(status().isUnauthorized());
    }

    // ─── PUT /api/organisations/{orgId}/subscription ──────────────────────────

    @Test
    @DisplayName("upsertSubscription_shouldReturn200_andCreateSubscription")
    void upsertSubscription_shouldReturn200_andCreateSubscription() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.tier.id").value(tierId.toString()))
                .andExpect(jsonPath("$.addons").isArray())
                .andExpect(jsonPath("$.totalMonthly").isNumber());
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn200_andUpdateExistingSubscription")
    void upsertSubscription_shouldReturn200_andUpdateExistingSubscription() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isOk());

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "ANNUAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingCycle").value("ANNUAL"));
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn200_andPersistAddons_withMonthlyPricing")
    void upsertSubscription_shouldReturn200_andPersistAddons_withMonthlyPricing() throws Exception {
        var tier0 = tierRepository.findAll().get(0);
        var addon0 = addonRepository.findAll().stream().filter(a -> a.isAvailable()).findFirst().orElseThrow();

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tierId", tierId,
                                "billingCycle", "MONTHLY",
                                "addonIds", List.of(addonId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons.length()").value(1))
                .andExpect(jsonPath("$.addons[0].id").value(addonId.toString()))
                .andExpect(jsonPath("$.totalMonthly").value(tier0.getPriceMonthly() + addon0.getPriceMonthly()));
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn200_andPersistAddons_withAnnualPricing")
    void upsertSubscription_shouldReturn200_andPersistAddons_withAnnualPricing() throws Exception {
        var tier0 = tierRepository.findAll().get(0);
        var addon0 = addonRepository.findAll().stream().filter(a -> a.isAvailable()).findFirst().orElseThrow();

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tierId", tierId,
                                "billingCycle", "ANNUAL",
                                "addonIds", List.of(addonId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons.length()").value(1))
                .andExpect(jsonPath("$.addons[0].id").value(addonId.toString()))
                .andExpect(jsonPath("$.totalMonthly").value(tier0.getPriceAnnual() + addon0.getPriceAnnual()));
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn200_andMemberCanReadAfterCreate")
    void upsertSubscription_shouldReturn200_andMemberCanReadAfterCreate() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"));
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn403_whenCallerIsNotOwner")
    void upsertSubscription_shouldReturn403_whenCallerIsNotOwner() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn404_whenCallerIsNotMember")
    void upsertSubscription_shouldReturn404_whenCallerIsNotMember() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn400_whenBillingCycleIsInvalid")
    void upsertSubscription_shouldReturn400_whenBillingCycleIsInvalid() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "WEEKLY"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn400_whenTierIdIsMissing")
    void upsertSubscription_shouldReturn400_whenTierIdIsMissing() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("billingCycle", "MONTHLY"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn404_whenTierNotFound")
    void upsertSubscription_shouldReturn404_whenTierNotFound() throws Exception {
        UUID unknownTierId = UUID.randomUUID();

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", unknownTierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn401_whenUnauthenticated")
    void upsertSubscription_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isUnauthorized());
    }

    // ─── validateDowngrade ────────────────────────────────────────────────────

    @Test
    @DisplayName("upsertSubscription_shouldReturn403_whenMemberCountExceedsTierLimit")
    void upsertSubscription_shouldReturn403_whenMemberCountExceedsTierLimit() throws Exception {
        // First tier allows maxMembers=5; add 4 extra users to exceed it (owner + member + 4 = 6)
        for (int i = 0; i < 4; i++) {
            UUID extraUserId = UUID.randomUUID();
            userRepository.save(UserModel.builder().id(extraUserId).email("extra" + i + "@example.com").name("Extra" + i).build());
            organisationRepository.saveMember(orgId, extraUserId, OrganisationRole.MEMBER);
        }

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("members")));
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn403_whenActiveProjectCountExceedsTierLimit")
    void upsertSubscription_shouldReturn403_whenActiveProjectCountExceedsTierLimit() throws Exception {
        // First tier allows maxProjects=3; create 4 active projects in this org
        for (int i = 0; i < 4; i++) {
            projectRepository.save(ProjectModel.builder()
                    .friendlyId("PRJ-TEST-" + i)
                    .name("Project " + i)
                    .ownerId(ownerId)
                    .organisationId(orgId)
                    .build());
        }

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("active projects")));
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn200_whenTierHasUnlimitedProjectsRegardlessOfProjectCount")
    void upsertSubscription_shouldReturn200_whenTierHasUnlimitedProjectsRegardlessOfProjectCount() throws Exception {
        // Unlimited-projects tier (maxProjects=null); create many active projects — should still pass
        UUID unlimitedTierId = tierRepository.findAll().stream()
                .filter(t -> t.getMaxProjects() == null)
                .findFirst()
                .orElseThrow()
                .getId();

        for (int i = 0; i < 5; i++) {
            projectRepository.save(ProjectModel.builder()
                    .friendlyId("PRJ-TEST-" + i)
                    .name("Project " + i)
                    .ownerId(ownerId)
                    .organisationId(orgId)
                    .build());
        }

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", unlimitedTierId, "billingCycle", "MONTHLY"))))
                .andExpect(status().isOk());
    }
}
