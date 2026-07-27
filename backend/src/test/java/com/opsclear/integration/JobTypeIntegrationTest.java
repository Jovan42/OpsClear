package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.JobTypeColor;
import com.opsclear.model.JobTypeModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobTypeRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

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
class JobTypeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JobTypeRepository jobTypeRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private com.opsclear.repository.FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID projectId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        jobTypeRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId    = UUID.randomUUID();
        memberId   = UUID.randomUUID();
        outsiderId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, outsiderId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        UUID jobTypesAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("JOB_TYPES")).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(jobTypesAddonId));
    }

    // --- POST /api/projects/{projectId}/job-types ---

    @Test
    @DisplayName("OWNER should create a job type and receive 201")
    void createType_shouldReturn201_forOwner() throws Exception {
        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bug", "color": "RED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bug"))
                .andExpect(jsonPath("$.color").value("RED"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.displayOrder").value(0))
                .andExpect(jsonPath("$.id").isString());
    }

    @Test
    @DisplayName("Second created type should get the next display order")
    void createType_shouldIncrementDisplayOrder() throws Exception {
        jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build());

        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Feature", "color": "BLUE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    @DisplayName("Should return 403 when requester belongs to the org but not the project")
    void createType_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bug", "color": "RED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of this project"));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from creating a job type")
    void createType_shouldReturn403_forMember() throws Exception {
        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bug", "color": "RED"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 400 when type name is blank")
    void createType_shouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "color": "RED"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when color is missing")
    void createType_shouldReturn400_whenColorMissing() throws Exception {
        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bug"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when color is not one of the fixed values")
    void createType_shouldReturn400_whenColorInvalid() throws Exception {
        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bug", "color": "MAGENTA"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist on create")
    void createType_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.jobTypes(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bug", "color": "RED"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 403 when the JOB_TYPES addon is not active")
    void createType_shouldReturn403_whenAddonNotActive() throws Exception {
        subscriptionRepository.deleteAll();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of());

        mockMvc.perform(post(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bug", "color": "RED"}
                                """))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/projects/{projectId}/job-types ---

    @Test
    @DisplayName("Any member should list job types ordered by display order")
    void listTypes_shouldReturn200_forMember() throws Exception {
        jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Feature").color(JobTypeColor.BLUE).displayOrder(1).build());
        jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build());

        mockMvc.perform(get(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Bug"))
                .andExpect(jsonPath("$[1].name").value("Feature"));
    }

    @Test
    @DisplayName("Should return 403 when requester belongs to the org but not the project on list")
    void listTypes_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(get(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of this project"));
    }

    // --- PUT /api/projects/{projectId}/job-types/{typeId} ---

    @Test
    @DisplayName("OWNER should update a job type")
    void updateType_shouldReturn200_forOwner() throws Exception {
        JobTypeModel type = jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build());

        mockMvc.perform(put(ApiPaths.jobType(projectId, type.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Defect", "color": "ORANGE", "displayOrder": 1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Defect"))
                .andExpect(jsonPath("$.color").value("ORANGE"))
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from updating a job type")
    void updateType_shouldReturn403_forMember() throws Exception {
        JobTypeModel type = jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build());

        mockMvc.perform(put(ApiPaths.jobType(projectId, type.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Defect", "color": "ORANGE", "displayOrder": 1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when type does not exist on update")
    void updateType_shouldReturn404_whenTypeNotFound() throws Exception {
        mockMvc.perform(put(ApiPaths.jobType(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Defect", "color": "ORANGE", "displayOrder": 1}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when type belongs to a different project on update")
    void updateType_shouldReturn404_whenTypeInDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobTypeModel otherType = jobTypeRepository.save(JobTypeModel.builder()
                .projectId(otherProject.getId()).name("Other").color(JobTypeColor.GRAY).displayOrder(0).build());

        mockMvc.perform(put(ApiPaths.jobType(projectId, otherType.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "color": "GRAY", "displayOrder": 0}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/projects/{projectId}/job-types/{typeId} ---

    @Test
    @DisplayName("OWNER should delete an unreferenced job type and receive 204")
    void deleteType_shouldReturn204_whenUnreferenced() throws Exception {
        JobTypeModel type = jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build());

        mockMvc.perform(delete(ApiPaths.jobType(projectId, type.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.jobTypes(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Should return 409 when a job still references the type")
    void deleteType_shouldReturn409_whenJobReferencesType() throws Exception {
        JobTypeModel type = jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build());
        jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Fix login bug").status(JobStatus.NEW)
                .typeId(type.getId()).createdBy(ownerId).build());

        mockMvc.perform(delete(ApiPaths.jobType(projectId, type.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete type: 1 job(s) still use this type"));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from deleting a job type")
    void deleteType_shouldReturn403_forMember() throws Exception {
        JobTypeModel type = jobTypeRepository.save(JobTypeModel.builder()
                .projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build());

        mockMvc.perform(delete(ApiPaths.jobType(projectId, type.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when type does not exist on delete")
    void deleteType_shouldReturn404_whenTypeNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.jobType(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }
}
