package com.opsclear.service;

import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockReasonServiceTest {

    @Mock private BlockReasonRepository blockReasonRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private BlockReasonService blockReasonService;

    private UUID projectId;
    private UUID ownerId;
    private UUID memberId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;

    @BeforeEach
    void setUp() {
        blockReasonService = new BlockReasonService(blockReasonRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        project = ProjectModel.builder()
                .id(projectId)
                .name("Test Project")
                .ownerId(ownerId)
                .build();

        ownerMembership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(ownerId)
                .role(ProjectMemberRole.OWNER)
                .build();

        memberMembership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(memberId)
                .role(ProjectMemberRole.MEMBER)
                .build();
    }

    // --- listActive ---

    @Test
    @DisplayName("Any member should be able to list active block reasons")
    void listActive_shouldReturnActiveReasons_forMember() {
        List<BlockReasonModel> reasons = List.of(
                BlockReasonModel.builder().id(UUID.randomUUID()).projectId(projectId).reason("Waiting for approval").build()
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(blockReasonRepository.findActiveByProjectId(projectId)).thenReturn(reasons);

        List<BlockReasonModel> result = blockReasonService.listActive(projectId, memberId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo("Waiting for approval");
    }

    @Test
    @DisplayName("Should throw NotFoundException when project does not exist on listActive")
    void listActive_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockReasonService.listActive(projectId, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member on listActive")
    void listActive_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockReasonService.listActive(projectId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- findOrCreate ---

    @Test
    @DisplayName("Should find or create a block reason, stripping leading/trailing whitespace")
    void findOrCreate_shouldReturnBlockReason_strippingReason() {
        BlockReasonModel expected = BlockReasonModel.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .reason("Waiting for approval")
                .build();

        when(blockReasonRepository.findOrCreate(projectId, "Waiting for approval")).thenReturn(expected);

        BlockReasonModel result = blockReasonService.findOrCreate(projectId, "  Waiting for approval  ");

        assertThat(result.getId()).isEqualTo(expected.getId());
        assertThat(result.getReason()).isEqualTo("Waiting for approval");
        verify(blockReasonRepository).findOrCreate(projectId, "Waiting for approval");
    }

    // --- softDelete ---

    @Test
    @DisplayName("OWNER should be able to soft delete a block reason")
    void softDelete_shouldSoftDeleteReason_forOwner() {
        UUID reasonId = UUID.randomUUID();
        BlockReasonModel reason = BlockReasonModel.builder()
                .id(reasonId)
                .projectId(projectId)
                .reason("Waiting for approval")
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(blockReasonRepository.findByIdAndDeletedAtIsNull(reasonId)).thenReturn(Optional.of(reason));

        blockReasonService.softDelete(projectId, reasonId, ownerId);

        verify(blockReasonRepository).softDelete(reasonId);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member on softDelete")
    void softDelete_shouldThrow_whenNotMember() {
        UUID reasonId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockReasonService.softDelete(projectId, reasonId, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("MEMBER should be forbidden from soft deleting a block reason")
    void softDelete_shouldThrow_whenMemberRole() {
        UUID reasonId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> blockReasonService.softDelete(projectId, reasonId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw NotFoundException when block reason does not exist on softDelete")
    void softDelete_shouldThrow_whenReasonNotFound() {
        UUID reasonId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(blockReasonRepository.findByIdAndDeletedAtIsNull(reasonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockReasonService.softDelete(projectId, reasonId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Block reason not found");
    }
}
