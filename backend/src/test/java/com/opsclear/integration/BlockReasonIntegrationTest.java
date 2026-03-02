package com.opsclear.integration;

import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockReasonIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
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

    // --- GET /api/projects/{projectId}/block-reasons ---

    @Test
    @DisplayName("Any member should be able to list active block reasons")
    void listBlockReasons_shouldReturn200_forMember() throws Exception {
        blockReasonRepository.findOrCreate(projectId, "Waiting for approval");
        blockReasonRepository.findOrCreate(projectId, "Missing access credentials");

        mockMvc.perform(get("/api/projects/" + projectId + "/block-reasons")
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reason").value("Waiting for approval"))
                .andExpect(jsonPath("$[1].reason").value("Missing access credentials"));
    }

    @Test
    @DisplayName("Should return empty list when no block reasons exist")
    void listBlockReasons_shouldReturnEmptyList_whenNoneExist() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId + "/block-reasons")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Soft-deleted block reasons should not appear in the list")
    void listBlockReasons_shouldExcludeSoftDeleted() throws Exception {
        BlockReasonModel active = blockReasonRepository.findOrCreate(projectId, "Active reason");
        BlockReasonModel toDelete = blockReasonRepository.findOrCreate(projectId, "Deleted reason");
        blockReasonRepository.softDelete(toDelete.getId());

        mockMvc.perform(get("/api/projects/" + projectId + "/block-reasons")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(active.getId().toString()));
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member")
    void listBlockReasons_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();

        mockMvc.perform(get("/api/projects/" + projectId + "/block-reasons")
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist")
    void listBlockReasons_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(get("/api/projects/" + UUID.randomUUID() + "/block-reasons")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/projects/{projectId}/block-reasons/{reasonId} ---

    @Test
    @DisplayName("OWNER should be able to soft delete a block reason and return 204")
    void deleteBlockReason_shouldReturn204_forOwner() throws Exception {
        BlockReasonModel reason = blockReasonRepository.findOrCreate(projectId, "Waiting for approval");

        mockMvc.perform(delete("/api/projects/" + projectId + "/block-reasons/" + reason.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(blockReasonRepository.findByIdAndDeletedAtIsNull(reason.getId())).isEmpty();
    }

    @Test
    @DisplayName("Soft-deleted reason should not appear in active list after deletion")
    void deleteBlockReason_shouldHideFromList_afterDeletion() throws Exception {
        BlockReasonModel reason = blockReasonRepository.findOrCreate(projectId, "To be removed");

        mockMvc.perform(delete("/api/projects/" + projectId + "/block-reasons/" + reason.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId + "/block-reasons")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Soft-deleted reason can be restored via find-or-create (blocking a job with same text)")
    void deleteBlockReason_canBeRestoredOnNextBlock() throws Exception {
        BlockReasonModel reason = blockReasonRepository.findOrCreate(projectId, "Same text");
        blockReasonRepository.softDelete(reason.getId());

        // Using findOrCreate with the same text restores the soft-deleted record
        BlockReasonModel restored = blockReasonRepository.findOrCreate(projectId, "Same text");

        assertThat(restored.getId()).isEqualTo(reason.getId());
        assertThat(blockReasonRepository.findByIdAndDeletedAtIsNull(restored.getId())).isPresent();
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member on delete")
    void deleteBlockReason_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        BlockReasonModel reason = blockReasonRepository.findOrCreate(projectId, "Waiting for approval");

        mockMvc.perform(delete("/api/projects/" + projectId + "/block-reasons/" + reason.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEMBER should be forbidden from deleting a block reason")
    void deleteBlockReason_shouldReturn403_forMember() throws Exception {
        BlockReasonModel reason = blockReasonRepository.findOrCreate(projectId, "Waiting for approval");

        mockMvc.perform(delete("/api/projects/" + projectId + "/block-reasons/" + reason.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when block reason does not exist")
    void deleteBlockReason_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/projects/" + projectId + "/block-reasons/" + UUID.randomUUID())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist on delete")
    void deleteBlockReason_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(delete("/api/projects/" + UUID.randomUUID() + "/block-reasons/" + UUID.randomUUID())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }
}
