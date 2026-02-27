package com.opsclear.service;

import com.opsclear.dto.AddMemberRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    private ProjectMemberService projectMemberService;

    private UUID projectId;
    private UUID ownerId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;

    @BeforeEach
    void setUp() {
        projectMemberService = new ProjectMemberService(
                projectMemberRepository, projectRepository, userRepository);

        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        project = ProjectModel.builder()
                .id(projectId)
                .name("Test Project")
                .ownerId(ownerId)
                .build();

        ownerMembership = ProjectMemberModel.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .userId(ownerId)
                .role(ProjectMemberRole.OWNER)
                .build();
    }

    // ─── listMembers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should list members for a project member")
    void listMembers_shouldReturnMembers_forMember() {
        UUID memberId = UUID.randomUUID();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(membership));
        when(projectMemberRepository.findByProjectId(projectId)).thenReturn(List.of(ownerMembership, membership));

        List<ProjectMemberModel> result = projectMemberService.listMembers(projectId, memberId);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Should throw NotFoundException when listing members of non-existent project")
    void listMembers_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMemberService.listMembers(projectId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when non-member tries to list members")
    void listMembers_shouldThrow_whenNotMember() {
        UUID outsiderId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, outsiderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMemberService.listMembers(projectId, outsiderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // ─── addMember ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should add a new MEMBER when requester is OWNER")
    void addMember_shouldSaveMember_whenOwnerAdds() {
        UUID newUserId = UUID.randomUUID();
        AddMemberRequest request = AddMemberRequest.builder()
                .userId(newUserId).role(ProjectMemberRole.MEMBER).build();
        ProjectMemberModel saved = ProjectMemberModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).userId(newUserId)
                .role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findById(newUserId))
                .thenReturn(Optional.of(UserModel.builder().id(newUserId).build()));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, newUserId)).thenReturn(false);
        when(projectMemberRepository.save(any())).thenReturn(saved);

        ProjectMemberModel result = projectMemberService.addMember(projectId, ownerId, request);

        assertThat(result.getRole()).isEqualTo(ProjectMemberRole.MEMBER);
        assertThat(result.getUserId()).isEqualTo(newUserId);

        ArgumentCaptor<ProjectMemberModel> captor = ArgumentCaptor.forClass(ProjectMemberModel.class);
        verify(projectMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectMemberRole.MEMBER);
        assertThat(captor.getValue().getProjectId()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when MEMBER tries to add another user")
    void addMember_shouldThrow_whenRequesterIsMember() {
        UUID memberId = UUID.randomUUID();
        ProjectMemberModel memberMembership = ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();
        AddMemberRequest request = AddMemberRequest.builder()
                .userId(UUID.randomUUID()).role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> projectMemberService.addMember(projectId, memberId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when non-member tries to add a member")
    void addMember_shouldThrow_whenNotMember() {
        UUID outsiderId = UUID.randomUUID();
        AddMemberRequest request = AddMemberRequest.builder()
                .userId(UUID.randomUUID()).role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, outsiderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMemberService.addMember(projectId, outsiderId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when trying to assign OWNER role")
    void addMember_shouldThrow_whenAssigningOwnerRole() {
        UUID newUserId = UUID.randomUUID();
        AddMemberRequest request = AddMemberRequest.builder()
                .userId(newUserId).role(ProjectMemberRole.OWNER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> projectMemberService.addMember(projectId, ownerId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot assign OWNER role");
    }

    @Test
    @DisplayName("Should throw NotFoundException when target user does not exist")
    void addMember_shouldThrow_whenUserNotFound() {
        UUID missingUserId = UUID.randomUUID();
        AddMemberRequest request = AddMemberRequest.builder()
                .userId(missingUserId).role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMemberService.addMember(projectId, ownerId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("Should throw ConflictException when user is already a member")
    void addMember_shouldThrow_whenAlreadyMember() {
        AddMemberRequest request = AddMemberRequest.builder()
                .userId(ownerId).role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.of(UserModel.builder().id(ownerId).build()));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, ownerId)).thenReturn(true);

        assertThatThrownBy(() -> projectMemberService.addMember(projectId, ownerId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User is already a member of this project");
    }

    // ─── updateRole ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should update role when requester is OWNER")
    void updateRole_shouldUpdateRole_whenOwnerUpdates() {
        UUID memberId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .id(membershipId).projectId(projectId).userId(memberId)
                .role(ProjectMemberRole.MEMBER).build();
        ProjectMemberModel updated = ProjectMemberModel.builder()
                .id(membershipId).projectId(projectId).userId(memberId)
                .role(ProjectMemberRole.ADMIN).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(membershipId)).thenReturn(Optional.of(membership));
        when(projectMemberRepository.save(any())).thenReturn(updated);

        ProjectMemberModel result = projectMemberService.updateRole(
                projectId, ownerId, membershipId, ProjectMemberRole.ADMIN);

        assertThat(result.getRole()).isEqualTo(ProjectMemberRole.ADMIN);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when trying to assign OWNER role via update")
    void updateRole_shouldThrow_whenAssigningOwnerRole() {
        UUID membershipId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> projectMemberService.updateRole(
                projectId, ownerId, membershipId, ProjectMemberRole.OWNER))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot assign OWNER role");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when trying to change the OWNER's role")
    void updateRole_shouldThrow_whenChangingOwnerRole() {
        UUID ownerMembershipId = ownerMembership.getId();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(ownerMembershipId)).thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> projectMemberService.updateRole(
                projectId, ownerId, ownerMembershipId, ProjectMemberRole.ADMIN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot change the project owner's role");
    }

    @Test
    @DisplayName("Should throw NotFoundException when membership not found")
    void updateRole_shouldThrow_whenMemberNotFound() {
        UUID missingMembershipId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(missingMembershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMemberService.updateRole(
                projectId, ownerId, missingMembershipId, ProjectMemberRole.ADMIN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when membership belongs to a different project")
    void updateRole_shouldThrow_whenMemberFromDifferentProject() {
        UUID membershipId = UUID.randomUUID();
        ProjectMemberModel memberFromOtherProject = ProjectMemberModel.builder()
                .id(membershipId).projectId(UUID.randomUUID()).userId(UUID.randomUUID())
                .role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(membershipId)).thenReturn(Optional.of(memberFromOtherProject));

        assertThatThrownBy(() -> projectMemberService.updateRole(
                projectId, ownerId, membershipId, ProjectMemberRole.ADMIN))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found");
    }

    // ─── removeMember ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should remove a member when requester is OWNER")
    void removeMember_shouldDelete_whenOwnerRemoves() {
        UUID memberId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .id(membershipId).projectId(projectId).userId(memberId)
                .role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(membershipId)).thenReturn(Optional.of(membership));

        projectMemberService.removeMember(projectId, ownerId, membershipId);

        verify(projectMemberRepository).delete(membershipId);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when trying to remove the project OWNER")
    void removeMember_shouldThrow_whenRemovingOwner() {
        UUID ownerMembershipId = ownerMembership.getId();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(ownerMembershipId)).thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> projectMemberService.removeMember(
                projectId, ownerId, ownerMembershipId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot remove the project owner");
    }

    @Test
    @DisplayName("Should throw NotFoundException when membership not found on remove")
    void removeMember_shouldThrow_whenMemberNotFound() {
        UUID missingMembershipId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(missingMembershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMemberService.removeMember(
                projectId, ownerId, missingMembershipId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when removing a membership from a different project")
    void removeMember_shouldThrow_whenMemberFromDifferentProject() {
        UUID membershipId = UUID.randomUUID();
        ProjectMemberModel memberFromOtherProject = ProjectMemberModel.builder()
                .id(membershipId).projectId(UUID.randomUUID()).userId(UUID.randomUUID())
                .role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.findById(membershipId)).thenReturn(Optional.of(memberFromOtherProject));

        assertThatThrownBy(() -> projectMemberService.removeMember(projectId, ownerId, membershipId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found");
    }
}
