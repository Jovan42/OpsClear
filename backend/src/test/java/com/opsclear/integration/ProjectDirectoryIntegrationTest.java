package com.opsclear.integration;

import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.FriendlyIdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers ADR-0045: the org-wide project directory endpoint (JOB-187) — Owner/Admin
 * gated, lists every project in the org regardless of the caller's own membership,
 * sorted by member count ascending.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectDirectoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private FriendlyIdRepository friendlyIdRepository;

    @Autowired
    private FriendlyIdService friendlyIdService;

    @Autowired
    private UserRepository userRepository;

    private UUID ownerId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(ownerId)
                .email("owner@example.com")
                .name("Test Owner")
                .build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId);
    }

    @Test
    @DisplayName("Owner sees every project in the org, sorted by member count ascending, including ones they're not a member of")
    void getDirectory_shouldReturnAllOrgProjects_sortedByMemberCountAscending() throws Exception {
        ProjectModel busy = createProject("Busy Project", ProjectStatus.ACTIVE);
        addMember(busy.getId(), ownerId, ProjectMemberRole.OWNER);
        UUID extraMemberId = createAndAddMember(busy.getId(), ProjectMemberRole.MEMBER);

        ProjectModel orphaned = createProject("Orphaned Project", ProjectStatus.COMPLETED);
        // Deliberately no members added — this is the blind-spot case ADR-0045 exists for.

        mockMvc.perform(get(ApiPaths.projectDirectory(orgId))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(ownerId.toString())
                                .claim("email", "owner@example.com")
                                .claim("name", "Test Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Orphaned Project"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].memberCount").value(0))
                .andExpect(jsonPath("$[1].name").value("Busy Project"))
                .andExpect(jsonPath("$[1].memberCount").value(2));
    }

    @Test
    @DisplayName("Admin can view the directory")
    void getDirectory_shouldReturn200_forAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(adminId).email("admin@example.com").name("Admin").build());
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.ADMIN);
        createProject("Solo Project", ProjectStatus.ACTIVE);

        mockMvc.perform(get(ApiPaths.projectDirectory(orgId))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(adminId.toString())
                                .claim("email", "admin@example.com")
                                .claim("name", "Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("A plain member is forbidden from viewing the directory")
    void getDirectory_shouldReturn403_forPlainMember() throws Exception {
        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);

        mockMvc.perform(get(ApiPaths.projectDirectory(orgId))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(memberId.toString())
                                .claim("email", "member@example.com")
                                .claim("name", "Member"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user outside the org is not found rather than leaking directory data")
    void getDirectory_shouldReturn404_whenCallerNotInOrg() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());

        mockMvc.perform(get(ApiPaths.projectDirectory(orgId))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(outsiderId.toString())
                                .claim("email", "outsider@example.com")
                                .claim("name", "Outsider"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Requires authentication")
    void getDirectory_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.projectDirectory(orgId)))
                .andExpect(status().isUnauthorized());
    }

    private ProjectModel createProject(String name, ProjectStatus status) {
        return projectRepository.save(ProjectModel.builder()
                .friendlyId(friendlyIdService.nextFriendlyId(orgId, com.opsclear.model.FriendlyIdEntityType.PROJECT))
                .name(name)
                .ownerId(ownerId)
                .organisationId(orgId)
                .status(status)
                .build());
    }

    private void addMember(UUID projectId, UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(userId)
                .role(role)
                .build());
    }

    private UUID createAndAddMember(UUID projectId, ProjectMemberRole role) {
        UUID userId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(userId)
                .email(userId + "@example.com")
                .name("Project Member")
                .build());
        addMember(projectId, userId, role);
        return userId;
    }
}
