package com.opsclear.integration;

import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Add-on gating — dashboard endpoint")
class AddonGatingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID orgId;
    private UUID projectId;
    private UUID tierId;
    private UUID dashboardAddonId;
    private UUID notesAddonId;

    @BeforeEach
    void setUp() {
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("owner@test.com").name("Owner").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());

        tierId = tierRepository.findAll().get(0).getId();
        dashboardAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("DASHBOARD")).findFirst().orElseThrow().getId();
        notesAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("NOTES")).findFirst().orElseThrow().getId();
    }

    @Test
    @DisplayName("Should return 200 when DASHBOARD add-on is active")
    void dashboardGet_shouldReturn200_whenAddonActive() throws Exception {
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(dashboardAddonId));

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 403 when DASHBOARD add-on is not in subscription")
    void dashboardGet_shouldReturn403_whenAddonNotActive() throws Exception {
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(notesAddonId));

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when org has no subscription")
    void dashboardGet_shouldReturn403_whenNoSubscription() throws Exception {
        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isForbidden());
    }
}
