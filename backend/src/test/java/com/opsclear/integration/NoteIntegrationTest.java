package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.MilestoneRepository;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;
    private UUID jobId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
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

        JobModel job = jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title("Install panel")
                .status(JobStatus.IN_PROGRESS)
                .createdBy(ownerId)
                .build());
        jobId = job.getId();

        UUID notesAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("NOTES")).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(notesAddonId));
    }

    // --- POST /api/projects/{projectId}/jobs/{jobId}/notes ---

    @Test
    @DisplayName("Any project member should be able to create a note and receive 201")
    void createNote_shouldReturn201_forMember() throws Exception {
        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Client confirmed deadline."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.authorId").value(memberId.toString()))
                .andExpect(jsonPath("$.content").value("Client confirmed deadline."))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("create should return 400 when content is blank")
    void createNote_shouldReturn400_whenContentBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create should return 400 when content exceeds 10000 characters")
    void createNote_shouldReturn400_whenContentTooLong() throws Exception {
        String tooLong = "x".repeat(10001);
        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", tooLong))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create should return 403 when caller is not a project member")
    void createNote_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Some note"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("create should return 404 when job does not exist")
    void createNote_shouldReturn404_whenJobNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.notes(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Some note"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("create should return 404 when job belongs to a different project")
    void createNote_shouldReturn404_whenJobInDifferentProject() throws Exception {
        UUID otherOwnerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(otherOwnerId).email("other@example.com").name("Other").build());
        organisationRepository.saveMember(orgId, otherOwnerId, OrganisationRole.OWNER);
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(otherOwnerId).organisationId(orgId).build());
        JobModel jobInOtherProject = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId())
                .title("Other job")
                .status(JobStatus.NEW)
                .createdBy(otherOwnerId)
                .build());

        mockMvc.perform(post(ApiPaths.notes(projectId, jobInOtherProject.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Some note"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("create should return 404 when project does not exist")
    void createNote_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.notes(UUID.randomUUID(), jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Some note"))))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/jobs/{jobId}/notes ---

    @Test
    @DisplayName("Any project member should be able to list notes for a job ordered oldest first")
    void listByJob_shouldReturn200_withNotesOldestFirst() throws Exception {
        noteRepository.insert(jobId, ownerId, "First note");
        noteRepository.insert(jobId, memberId, "Second note");

        mockMvc.perform(get(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("First note"))
                .andExpect(jsonPath("$[1].content").value("Second note"));
    }

    @Test
    @DisplayName("listByJob should return empty array when job has no notes")
    void listByJob_shouldReturnEmptyArray_whenNoNotes() throws Exception {
        mockMvc.perform(get(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("listByJob should return 403 when caller is not a project member")
    void listByJob_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        mockMvc.perform(get(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listByJob should return 404 when job does not exist")
    void listByJob_shouldReturn404_whenJobNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.notes(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/notes ---

    @Test
    @DisplayName("listByProject should return notes grouped by job, jobs ordered by most recent note")
    void listByProject_shouldReturn200_groupedByJob() throws Exception {
        JobModel job2 = jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title("Roof inspection")
                .status(JobStatus.NEW)
                .createdBy(ownerId)
                .build());

        noteRepository.insert(job2.getId(), ownerId, "Older note on job2");
        noteRepository.insert(jobId, memberId, "Note on job1");

        mockMvc.perform(get(ApiPaths.projectNotes(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobId").value(jobId.toString()))
                .andExpect(jsonPath("$[0].jobName").value("Install panel"))
                .andExpect(jsonPath("$[0].notes.length()").value(1))
                .andExpect(jsonPath("$[0].notes[0].content").value("Note on job1"))
                .andExpect(jsonPath("$[1].jobId").value(job2.getId().toString()))
                .andExpect(jsonPath("$[1].jobName").value("Roof inspection"))
                .andExpect(jsonPath("$[1].notes.length()").value(1));
    }

    @Test
    @DisplayName("listByProject should return empty array when project has no notes")
    void listByProject_shouldReturnEmptyArray_whenNoNotes() throws Exception {
        mockMvc.perform(get(ApiPaths.projectNotes(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("listByProject should return 403 when caller is not a project member")
    void listByProject_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        mockMvc.perform(get(ApiPaths.projectNotes(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listByProject should return 404 when project does not exist")
    void listByProject_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.projectNotes(UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- completed project guard ---

    @Test
    @DisplayName("createNote_shouldReturn409_whenProjectIsCompleted")
    void createNote_shouldReturn409_whenProjectIsCompleted() throws Exception {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);

        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Some note."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    // --- PAST_DUE subscription status (JOB-175) ---

    @Test
    @DisplayName("createNote should return 403 when org subscription is past due")
    void createNote_shouldReturn403_whenSubscriptionPastDue() throws Exception {
        markSubscriptionPastDue();

        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Some note"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listByJob should still return 200 when org subscription is past due")
    void listByJob_shouldReturn200_whenSubscriptionPastDue() throws Exception {
        noteRepository.insert(jobId, ownerId, "Existing note");
        markSubscriptionPastDue();

        mockMvc.perform(get(ApiPaths.notes(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private void markSubscriptionPastDue() {
        UUID subscriptionId = subscriptionRepository.findByOrgId(orgId).orElseThrow().getId();
        subscriptionRepository.updatePaddleCustomerId(subscriptionId, orgId, "ctm_test_past_due");
        subscriptionRepository.updateFromPaddleWebhook("ctm_test_past_due", "sub_test_past_due", "PAST_DUE");
    }
}
