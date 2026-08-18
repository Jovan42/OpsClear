package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobRelationshipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRelationshipRepository jobRelationshipRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobRelationshipRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
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

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        UUID relationshipsAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("JOB_RELATIONSHIPS")).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(relationshipsAddonId));
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, "ACTIVE", null, null);
    }

    // --- POST /api/projects/{projectId}/jobs/{jobId}/relationships ---

    @Test
    @DisplayName("Member should create a BLOCKED_BY relationship and receive 201")
    void create_shouldReturn201_forMember() throws Exception {
        JobModel source = createJob("Deploy backend");
        JobModel target = createJob("Run DB migration");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "BLOCKED_BY"}
                                """.formatted(target.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.type").value("BLOCKED_BY"))
                .andExpect(jsonPath("$.sourceJobId").value(source.getId().toString()))
                .andExpect(jsonPath("$.targetJobId").value(target.getId().toString()));

        assertThat(jobRelationshipRepository.findByJobId(source.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Should create a RELATED_TO relationship")
    void create_shouldReturn201_forRelatedTo() throws Exception {
        JobModel source = createJob("Frontend task");
        JobModel target = createJob("Backend task");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RELATED_TO"));
    }

    @Test
    @DisplayName("Should return 400 when job references itself")
    void create_shouldReturn400_whenSelfReference() throws Exception {
        JobModel job = createJob("Solo job");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(job.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 404 when target job belongs to a different project")
    void create_shouldReturn404_whenTargetJobInDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobModel otherJob = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId()).title("Other job")
                .status(JobStatus.NEW).createdBy(ownerId).build());

        JobModel source = createJob("Source job");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(otherJob.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when source job does not exist")
    void create_shouldReturn404_whenSourceJobNotFound() throws Exception {
        JobModel target = createJob("Target job");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 409 when the same relationship already exists")
    void create_shouldReturn409_whenDuplicate() throws Exception {
        JobModel source = createJob("Job A");
        JobModel target = createJob("Job B");

        String body = """
                {"targetJobId": "%s", "type": "BLOCKED_BY"}
                """.formatted(target.getId());

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist on create")
    void create_shouldReturn404_whenProjectNotFound() throws Exception {
        JobModel target = createJob("Target");

        mockMvc.perform(post(ApiPaths.jobRelationships(UUID.randomUUID(), UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist on delete")
    void delete_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.jobRelationship(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member")
    void create_shouldReturn403_whenNotMember() throws Exception {
        JobModel source = createJob("Source");
        JobModel target = createJob("Target");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 400 when targetJobId is missing")
    void create_shouldReturn400_whenTargetJobIdMissing() throws Exception {
        JobModel source = createJob("Source");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "RELATED_TO"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when type is missing")
    void create_shouldReturn400_whenTypeMissing() throws Exception {
        JobModel source = createJob("Source");
        JobModel target = createJob("Target");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s"}
                                """.formatted(target.getId())))
                .andExpect(status().isBadRequest());
    }

    // --- DELETE /api/projects/{projectId}/jobs/{jobId}/relationships/{relationshipId} ---

    @Test
    @DisplayName("Member should delete a relationship and receive 204")
    void delete_shouldReturn204_forMember() throws Exception {
        JobModel source = createJob("Job A");
        JobModel target = createJob("Job B");

        String locationHeader = mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract the relationship id from the response
        UUID relationshipId = UUID.fromString(
                locationHeader.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        mockMvc.perform(delete(ApiPaths.jobRelationship(projectId, source.getId(), relationshipId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(jobRelationshipRepository.findByJobId(source.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should return 204 when deleting from the target job's side")
    void delete_shouldReturn204_fromTargetSide() throws Exception {
        JobModel source = createJob("Job A");
        JobModel target = createJob("Job B");

        String body = mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andReturn().getResponse().getContentAsString();

        UUID relationshipId = UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        // Delete from target job's perspective
        mockMvc.perform(delete(ApiPaths.jobRelationship(projectId, target.getId(), relationshipId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(jobRelationshipRepository.findByJobId(source.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should return 404 when relationship does not exist")
    void delete_shouldReturn404_whenNotFound() throws Exception {
        JobModel job = createJob("Some job");

        mockMvc.perform(delete(ApiPaths.jobRelationship(projectId, job.getId(), UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when relationship does not belong to the given job")
    void delete_shouldReturn404_whenRelationshipBelongsToDifferentJob() throws Exception {
        JobModel jobA = createJob("Job A");
        JobModel jobB = createJob("Job B");
        JobModel unrelatedJob = createJob("Unrelated Job");

        String body = mockMvc.perform(post(ApiPaths.jobRelationships(projectId, jobA.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(jobB.getId())))
                .andReturn().getResponse().getContentAsString();

        UUID relationshipId = UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        // Try to delete using a job that is not part of the relationship
        mockMvc.perform(delete(ApiPaths.jobRelationship(projectId, unrelatedJob.getId(), relationshipId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member on delete")
    void delete_shouldReturn403_whenNotMember() throws Exception {
        JobModel source = createJob("Job A");
        JobModel target = createJob("Job B");

        String body = mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andReturn().getResponse().getContentAsString();

        UUID relationshipId = UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        mockMvc.perform(delete(ApiPaths.jobRelationship(projectId, source.getId(), relationshipId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com"))))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/projects/{projectId}/jobs/{jobId} — relationships in JobResponse ---

    @Test
    @DisplayName("Job detail should include OUTGOING relationship")
    void getJob_shouldIncludeOutgoingRelationship() throws Exception {
        JobModel source = createJob("Source job");
        JobModel target = createJob("Target job");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "BLOCKED_BY"}
                                """.formatted(target.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.job(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships").isArray())
                .andExpect(jsonPath("$.relationships.length()").value(1))
                .andExpect(jsonPath("$.relationships[0].type").value("BLOCKED_BY"))
                .andExpect(jsonPath("$.relationships[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.relationships[0].job.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.relationships[0].job.friendlyId").value(target.getFriendlyId()))
                .andExpect(jsonPath("$.relationships[0].job.title").value("Target job"))
                .andExpect(jsonPath("$.relationships[0].job.status").value("NEW"));
    }

    @Test
    @DisplayName("Job detail should include INCOMING relationship on the other side")
    void getJob_shouldIncludeIncomingRelationship() throws Exception {
        JobModel source = createJob("Source job");
        JobModel target = createJob("Target job");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.job(projectId, target.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships.length()").value(1))
                .andExpect(jsonPath("$.relationships[0].type").value("RELATED_TO"))
                .andExpect(jsonPath("$.relationships[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.relationships[0].job.id").value(source.getId().toString()))
                .andExpect(jsonPath("$.relationships[0].job.friendlyId").value(source.getFriendlyId()))
                .andExpect(jsonPath("$.relationships[0].job.title").value("Source job"));
    }

    @Test
    @DisplayName("Job detail should return empty relationships when job has none")
    void getJob_shouldReturnEmptyRelationships_whenNone() throws Exception {
        JobModel job = createJob("Standalone job");

        mockMvc.perform(get(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships").isArray())
                .andExpect(jsonPath("$.relationships.length()").value(0));
    }

    @Test
    @DisplayName("Job detail should return relationship with null title and status when linked job is deleted")
    void getJob_shouldHandleSoftDeletedLinkedJob() throws Exception {
        JobModel source = createJob("Source job");
        JobModel target = createJob("Target job");

        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(target.getId())))
                .andExpect(status().isCreated());

        // Soft-delete the linked job
        mockMvc.perform(delete(ApiPaths.job(projectId, target.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.job(projectId, source.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships.length()").value(1))
                .andExpect(jsonPath("$.relationships[0].job.friendlyId").doesNotExist())
                .andExpect(jsonPath("$.relationships[0].job.title").doesNotExist())
                .andExpect(jsonPath("$.relationships[0].job.status").doesNotExist());
    }

    @Test
    @DisplayName("Job detail should include both OUTGOING and INCOMING relationships")
    void getJob_shouldIncludeBothSides_whenJobHasMultipleRelationships() throws Exception {
        JobModel center = createJob("Center job");
        JobModel blocker = createJob("Blocker job");
        JobModel related = createJob("Related job");

        // center is blocked by blocker (center is source → OUTGOING)
        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, center.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "BLOCKED_BY"}
                                """.formatted(blocker.getId())))
                .andExpect(status().isCreated());

        // related points to center (related is source, center is target → INCOMING on center)
        mockMvc.perform(post(ApiPaths.jobRelationships(projectId, related.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetJobId": "%s", "type": "RELATED_TO"}
                                """.formatted(center.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.job(projectId, center.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships.length()").value(2));
    }

    // --- Helpers ---

    private JobModel createJob(String title) {
        return jobRepository.save(JobModel.builder()
                .friendlyId("JOB-" + UUID.randomUUID().toString().substring(0, 8))
                .projectId(projectId)
                .title(title)
                .status(JobStatus.NEW)
                .createdBy(ownerId)
                .build());
    }
}
