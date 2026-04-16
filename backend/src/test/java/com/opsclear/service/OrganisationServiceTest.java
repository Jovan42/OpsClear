package com.opsclear.service;

import com.opsclear.dto.CreateOrganisationRequest;
import com.opsclear.dto.UpdateOrganisationRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganisationService")
class OrganisationServiceTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FriendlyIdService friendlyIdService;

    private OrganisationService organisationService;

    private UUID ownerId;
    private UUID memberId;
    private UUID orgId;
    private UserModel owner;
    private OrganisationModel org;

    @BeforeEach
    void setUp() {
        organisationService = new OrganisationService(organisationRepository, userRepository, friendlyIdService);

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        owner = UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build();

        org = OrganisationModel.builder()
                .id(orgId)
                .name("Acme Corp")
                .slug("ACM")
                .createdBy(ownerId)
                .createdByName("Owner")
                .createdAt(Instant.now())
                .build();
    }

    // --- create ---

    @Test
    @DisplayName("create_shouldCreateOrg_andAddCallerAsOwner")
    void create_shouldCreateOrg_andAddCallerAsOwner() {
        CreateOrganisationRequest request = new CreateOrganisationRequest("Acme Corp", "acm");

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(organisationRepository.existsBySlugAndDeletedAtIsNull("acm")).thenReturn(false);
        when(organisationRepository.save(any())).thenReturn(org);

        OrganisationModel result = organisationService.create(request, ownerId);

        assertThat(result.getName()).isEqualTo("Acme Corp");
        assertThat(result.getSlug()).isEqualTo("ACM");

        ArgumentCaptor<OrganisationModel> captor = ArgumentCaptor.forClass(OrganisationModel.class);
        verify(organisationRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ownerId);

        verify(organisationRepository).saveMember(orgId, ownerId, OrganisationRole.OWNER);
        verify(friendlyIdService).seedForOrg(orgId);
    }

    @Test
    @DisplayName("create_shouldThrowNotFoundException_whenUserDoesNotExist")
    void create_shouldThrowNotFoundException_whenUserDoesNotExist() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organisationService.create(
                new CreateOrganisationRequest("Acme", "ACM"), ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("create_shouldThrowConflictException_whenSlugAlreadyExists")
    void create_shouldThrowConflictException_whenSlugAlreadyExists() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(organisationRepository.existsBySlugAndDeletedAtIsNull("ACM")).thenReturn(true);

        assertThatThrownBy(() -> organisationService.create(
                new CreateOrganisationRequest("Acme", "ACM"), ownerId))
                .isInstanceOf(ConflictException.class);
    }

    // --- getById ---

    @Test
    @DisplayName("getById_shouldReturnOrg_whenCallerIsMember")
    void getById_shouldReturnOrg_whenCallerIsMember() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));

        OrganisationModel result = organisationService.getById(orgId, ownerId);

        assertThat(result.getId()).isEqualTo(orgId);
    }

    @Test
    @DisplayName("getById_shouldThrowNotFoundException_whenOrgDoesNotExist")
    void getById_shouldThrowNotFoundException_whenOrgDoesNotExist() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organisationService.getById(orgId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getById_shouldThrowForbiddenException_whenCallerIsNotMember")
    void getById_shouldThrowForbiddenException_whenCallerIsNotMember() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organisationService.getById(orgId, ownerId))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- update ---

    @Test
    @DisplayName("update_shouldUpdateNameAndSlug_whenCallerIsOwner")
    void update_shouldUpdateNameAndSlug_whenCallerIsOwner() {
        UpdateOrganisationRequest request = new UpdateOrganisationRequest("New Name", "NEW");
        OrganisationModel updated = OrganisationModel.builder()
                .id(orgId).name("New Name").slug("NEW").createdBy(ownerId).createdAt(Instant.now()).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.existsBySlugAndIdNotAndDeletedAtIsNull("NEW", orgId)).thenReturn(false);
        when(organisationRepository.save(any())).thenReturn(updated);

        OrganisationModel result = organisationService.update(orgId, request, ownerId);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getSlug()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("update_shouldThrowForbiddenException_whenCallerIsNotOwner")
    void update_shouldThrowForbiddenException_whenCallerIsNotOwner() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> organisationService.update(
                orgId, new UpdateOrganisationRequest("X", "XX"), memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("update_shouldThrowConflictException_whenSlugTakenByAnotherOrg")
    void update_shouldThrowConflictException_whenSlugTakenByAnotherOrg() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.existsBySlugAndIdNotAndDeletedAtIsNull("XYZ", orgId)).thenReturn(true);

        assertThatThrownBy(() -> organisationService.update(
                orgId, new UpdateOrganisationRequest("X", "XYZ"), ownerId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("update_shouldThrowForbiddenException_whenCallerIsNotMember")
    void update_shouldThrowForbiddenException_whenCallerIsNotMember() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organisationService.update(
                orgId, new UpdateOrganisationRequest("X", "XX"), memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- getMyOrganisation ---

    @Test
    @DisplayName("getMyOrganisation_shouldReturnOrg_whenCallerIsMember")
    void getMyOrganisation_shouldReturnOrg_whenCallerIsMember() {
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.of(org));

        var result = organisationService.getMyOrganisation(ownerId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(orgId);
    }

    @Test
    @DisplayName("getMyOrganisation_shouldReturnEmpty_whenCallerHasNoOrg")
    void getMyOrganisation_shouldReturnEmpty_whenCallerHasNoOrg() {
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());

        var result = organisationService.getMyOrganisation(ownerId);

        assertThat(result).isEmpty();
    }

    // --- softDelete ---

    @Test
    @DisplayName("softDelete_shouldMarkDeleted_whenCallerIsOwner")
    void softDelete_shouldMarkDeleted_whenCallerIsOwner() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.save(any())).thenReturn(org);

        organisationService.softDelete(orgId, ownerId);

        ArgumentCaptor<OrganisationModel> captor = ArgumentCaptor.forClass(OrganisationModel.class);
        verify(organisationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("softDelete_shouldThrowForbiddenException_whenCallerIsNotOwner")
    void softDelete_shouldThrowForbiddenException_whenCallerIsNotOwner() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.ADMIN));

        assertThatThrownBy(() -> organisationService.softDelete(orgId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }
}
