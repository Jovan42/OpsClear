package com.opsclear.service;

import com.opsclear.dto.AddOrgMemberRequest;
import com.opsclear.dto.UpdateOrgMemberRoleRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
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
@DisplayName("OrganisationMemberService")
class OrganisationMemberServiceTest {

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private UserRepository userRepository;

    private OrganisationMemberService service;

    private UUID orgId;
    private UUID ownerId;
    private OrganisationModel org;
    private OrgMemberModel ownerMember;

    @BeforeEach
    void setUp() {
        service = new OrganisationMemberService(organisationRepository, userRepository);

        orgId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        org = OrganisationModel.builder()
                .id(orgId)
                .name("Acme Corp")
                .slug("ACM")
                .createdBy(ownerId)
                .build();

        ownerMember = OrgMemberModel.builder()
                .organisationId(orgId)
                .userId(ownerId)
                .userName("Owner")
                .userEmail("owner@example.com")
                .role(OrganisationRole.OWNER)
                .build();
    }

    // ─── listMembers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listMembers_shouldReturnMembers_forOrgMember")
    void listMembers_shouldReturnMembers_forOrgMember() {
        UUID memberId = UUID.randomUUID();
        OrgMemberModel member = OrgMemberModel.builder()
                .organisationId(orgId).userId(memberId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId))
                .thenReturn(Optional.of(OrganisationRole.MEMBER));
        when(organisationRepository.findMembers(orgId)).thenReturn(List.of(ownerMember, member));

        List<OrgMemberModel> result = service.listMembers(orgId, memberId);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("listMembers_shouldThrow_whenOrgNotFound")
    void listMembers_shouldThrow_whenOrgNotFound() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembers(orgId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organisation not found");
    }

    @Test
    @DisplayName("listMembers_shouldThrow_whenCallerIsNotMember")
    void listMembers_shouldThrow_whenCallerIsNotMember() {
        UUID outsiderId = UUID.randomUUID();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, outsiderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembers(orgId, outsiderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this organisation");
    }

    // ─── addMember ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addMember_shouldAddMember_whenCallerIsOwner")
    void addMember_shouldAddMember_whenCallerIsOwner() {
        UUID newUserId = UUID.randomUUID();
        AddOrgMemberRequest request = AddOrgMemberRequest.builder()
                .userId(newUserId).role(OrganisationRole.MEMBER).build();
        OrgMemberModel saved = OrgMemberModel.builder()
                .organisationId(orgId).userId(newUserId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findById(newUserId))
                .thenReturn(Optional.of(UserModel.builder().id(newUserId).build()));
        when(organisationRepository.existsMember(orgId, newUserId)).thenReturn(false);
        when(organisationRepository.findMember(orgId, newUserId)).thenReturn(Optional.of(saved));

        OrgMemberModel result = service.addMember(orgId, ownerId, request);

        verify(organisationRepository).saveMember(orgId, newUserId, OrganisationRole.MEMBER);
        assertThat(result.getRole()).isEqualTo(OrganisationRole.MEMBER);
        assertThat(result.getUserId()).isEqualTo(newUserId);
    }

    @Test
    @DisplayName("addMember_shouldAddMember_whenCallerIsAdmin")
    void addMember_shouldAddMember_whenCallerIsAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        AddOrgMemberRequest request = AddOrgMemberRequest.builder()
                .userId(newUserId).role(OrganisationRole.MEMBER).build();
        OrgMemberModel saved = OrgMemberModel.builder()
                .organisationId(orgId).userId(newUserId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, adminId))
                .thenReturn(Optional.of(OrganisationRole.ADMIN));
        when(userRepository.findById(newUserId))
                .thenReturn(Optional.of(UserModel.builder().id(newUserId).build()));
        when(organisationRepository.existsMember(orgId, newUserId)).thenReturn(false);
        when(organisationRepository.findMember(orgId, newUserId)).thenReturn(Optional.of(saved));

        service.addMember(orgId, adminId, request);

        verify(organisationRepository).saveMember(orgId, newUserId, OrganisationRole.MEMBER);
    }

    @Test
    @DisplayName("addMember_shouldThrow_whenCallerIsMember")
    void addMember_shouldThrow_whenCallerIsMember() {
        UUID memberId = UUID.randomUUID();
        AddOrgMemberRequest request = AddOrgMemberRequest.builder()
                .userId(UUID.randomUUID()).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId))
                .thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.addMember(orgId, memberId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("addMember_shouldThrow_whenAssigningOwnerRole")
    void addMember_shouldThrow_whenAssigningOwnerRole() {
        AddOrgMemberRequest request = AddOrgMemberRequest.builder()
                .userId(UUID.randomUUID()).role(OrganisationRole.OWNER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));

        assertThatThrownBy(() -> service.addMember(orgId, ownerId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot assign OWNER role");
    }

    @Test
    @DisplayName("addMember_shouldThrow_whenUserNotFound")
    void addMember_shouldThrow_whenUserNotFound() {
        UUID missingId = UUID.randomUUID();
        AddOrgMemberRequest request = AddOrgMemberRequest.builder()
                .userId(missingId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(orgId, ownerId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("addMember_shouldThrow_whenAlreadyMember")
    void addMember_shouldThrow_whenAlreadyMember() {
        AddOrgMemberRequest request = AddOrgMemberRequest.builder()
                .userId(ownerId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.of(UserModel.builder().id(ownerId).build()));
        when(organisationRepository.existsMember(orgId, ownerId)).thenReturn(true);

        assertThatThrownBy(() -> service.addMember(orgId, ownerId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User is already a member of this organisation");
    }

    // ─── updateRole ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateRole_shouldChangeRole_whenCallerIsOwner")
    void updateRole_shouldChangeRole_whenCallerIsOwner() {
        UUID targetId = UUID.randomUUID();
        OrgMemberModel target = OrgMemberModel.builder()
                .organisationId(orgId).userId(targetId).role(OrganisationRole.MEMBER).build();
        OrgMemberModel updated = OrgMemberModel.builder()
                .organisationId(orgId).userId(targetId).role(OrganisationRole.ADMIN).build();
        UpdateOrgMemberRoleRequest request = new UpdateOrgMemberRoleRequest(OrganisationRole.ADMIN);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findMember(orgId, targetId))
                .thenReturn(Optional.of(target))
                .thenReturn(Optional.of(updated));

        OrgMemberModel result = service.updateRole(orgId, ownerId, targetId, request);

        verify(organisationRepository).updateMemberRole(orgId, targetId, OrganisationRole.ADMIN);
        assertThat(result.getRole()).isEqualTo(OrganisationRole.ADMIN);
    }

    @Test
    @DisplayName("updateRole_shouldThrow_whenCallerIsNotOwner")
    void updateRole_shouldThrow_whenCallerIsNotOwner() {
        UUID adminId = UUID.randomUUID();
        UpdateOrgMemberRoleRequest request = new UpdateOrgMemberRoleRequest(OrganisationRole.MEMBER);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, adminId))
                .thenReturn(Optional.of(OrganisationRole.ADMIN));

        assertThatThrownBy(() -> service.updateRole(orgId, adminId, UUID.randomUUID(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER role required");
    }

    @Test
    @DisplayName("updateRole_shouldThrow_whenAssigningOwnerRole")
    void updateRole_shouldThrow_whenAssigningOwnerRole() {
        UpdateOrgMemberRoleRequest request = new UpdateOrgMemberRoleRequest(OrganisationRole.OWNER);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));

        assertThatThrownBy(() -> service.updateRole(orgId, ownerId, UUID.randomUUID(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot assign OWNER role");
    }

    @Test
    @DisplayName("updateRole_shouldThrow_whenChangingOwnerRole")
    void updateRole_shouldThrow_whenChangingOwnerRole() {
        UpdateOrgMemberRoleRequest request = new UpdateOrgMemberRoleRequest(OrganisationRole.ADMIN);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findMember(orgId, ownerId))
                .thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> service.updateRole(orgId, ownerId, ownerId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot change the organisation owner's role");
    }

    @Test
    @DisplayName("updateRole_shouldThrow_whenTargetMemberNotFound")
    void updateRole_shouldThrow_whenTargetMemberNotFound() {
        UUID missingId = UUID.randomUUID();
        UpdateOrgMemberRoleRequest request = new UpdateOrgMemberRoleRequest(OrganisationRole.ADMIN);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findMember(orgId, missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(orgId, ownerId, missingId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found in this organisation");
    }

    // ─── removeMember ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeMember_shouldDelete_whenCallerIsOwner")
    void removeMember_shouldDelete_whenCallerIsOwner() {
        UUID targetId = UUID.randomUUID();
        OrgMemberModel target = OrgMemberModel.builder()
                .organisationId(orgId).userId(targetId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findMember(orgId, targetId)).thenReturn(Optional.of(target));

        service.removeMember(orgId, ownerId, targetId);

        verify(organisationRepository).deleteMember(orgId, targetId);
    }

    @Test
    @DisplayName("removeMember_shouldDelete_whenCallerIsAdmin")
    void removeMember_shouldDelete_whenCallerIsAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        OrgMemberModel target = OrgMemberModel.builder()
                .organisationId(orgId).userId(targetId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, adminId))
                .thenReturn(Optional.of(OrganisationRole.ADMIN));
        when(organisationRepository.findMember(orgId, targetId)).thenReturn(Optional.of(target));

        service.removeMember(orgId, adminId, targetId);

        verify(organisationRepository).deleteMember(orgId, targetId);
    }

    @Test
    @DisplayName("removeMember_shouldThrow_whenCallerIsMember")
    void removeMember_shouldThrow_whenCallerIsMember() {
        UUID memberId = UUID.randomUUID();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId))
                .thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.removeMember(orgId, memberId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("removeMember_shouldThrow_whenRemovingOwner")
    void removeMember_shouldThrow_whenRemovingOwner() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findMember(orgId, ownerId)).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> service.removeMember(orgId, ownerId, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cannot remove the organisation owner");
    }

    @Test
    @DisplayName("removeMember_shouldThrow_whenTargetMemberNotFound")
    void removeMember_shouldThrow_whenTargetMemberNotFound() {
        UUID missingId = UUID.randomUUID();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findMember(orgId, missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(orgId, ownerId, missingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found in this organisation");
    }

    // ─── post-write reload edge cases ─────────────────────────────────────────

    @Test
    @DisplayName("addMember_shouldThrow_whenReloadAfterSaveFails")
    void addMember_shouldThrow_whenReloadAfterSaveFails() {
        UUID newUserId = UUID.randomUUID();
        AddOrgMemberRequest request = AddOrgMemberRequest.builder()
                .userId(newUserId).role(OrganisationRole.MEMBER).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findById(newUserId))
                .thenReturn(Optional.of(UserModel.builder().id(newUserId).build()));
        when(organisationRepository.existsMember(orgId, newUserId)).thenReturn(false);
        when(organisationRepository.findMember(orgId, newUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(orgId, ownerId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found in this organisation");
    }

    @Test
    @DisplayName("updateRole_shouldThrow_whenReloadAfterUpdateFails")
    void updateRole_shouldThrow_whenReloadAfterUpdateFails() {
        UUID targetId = UUID.randomUUID();
        OrgMemberModel target = OrgMemberModel.builder()
                .organisationId(orgId).userId(targetId).role(OrganisationRole.MEMBER).build();
        UpdateOrgMemberRoleRequest request = new UpdateOrgMemberRoleRequest(OrganisationRole.ADMIN);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId))
                .thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findMember(orgId, targetId))
                .thenReturn(Optional.of(target))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(orgId, ownerId, targetId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member not found in this organisation");
    }
}
