package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobRelationshipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRelationshipRepository jobRelationshipRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobRelationshipRepository.deleteAll();
        jobRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        ownerId  = UUID.randomUUID();
        memberId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());
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
                ProjectModel.builder().name("Other Project").ownerId(ownerId).build());
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

    // --- Helpers ---

    private JobModel createJob(String title) {
        return jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title(title)
                .status(JobStatus.NEW)
                .createdBy(ownerId)
                .build());
    }
}
