package com.opsclear.service;

import com.opsclear.dto.SendInviteRequest;
import com.opsclear.event.InviteAcceptedEvent;
import com.opsclear.event.InviteEmailRequestedEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.exception.TooManyRequestsException;
import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrgInviteService")
class OrgInviteServiceTest {

    @Mock private OrgInviteRepository orgInviteRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrgInviteService service;

    private UUID orgId;
    private UUID ownerId;
    private UUID memberId;
    private OrganisationModel org;
    private UserModel owner;

    @BeforeEach
    void setUp() {
        service = new OrgInviteService(orgInviteRepository, organisationRepository, userRepository, eventPublisher);

        orgId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        org = OrganisationModel.builder().id(orgId).name("Acme").slug("ACM").createdBy(ownerId).build();
        owner = UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build();
    }

    // ─── sendInvite ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendInvite_shouldCreateInviteAndRequestEmail_whenOwnerInvitesNewEmail")
    void sendInvite_shouldCreateInviteAndRequestEmail_whenOwnerInvitesNewEmail() {
        String targetEmail = "new@example.com";
        OrgInviteModel saved = pendingInvite(orgId, targetEmail, ownerId);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findByEmail(targetEmail)).thenReturn(Optional.empty());
        when(orgInviteRepository.existsPendingByEmailAndOrgId(targetEmail, orgId)).thenReturn(false);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(orgInviteRepository.save(any())).thenReturn(saved);

        OrgInviteModel result = service.sendInvite(orgId, ownerId, new SendInviteRequest(targetEmail, null));

        assertThat(result.getEmail()).isEqualTo(targetEmail);
        assertThat(result.getOrganisationId()).isEqualTo(orgId);

        ArgumentCaptor<OrgInviteModel> captor = ArgumentCaptor.forClass(OrgInviteModel.class);
        verify(orgInviteRepository).save(captor.capture());
        OrgInviteModel toSave = captor.getValue();
        assertThat(toSave.getToken()).hasSize(64);
        assertThat(toSave.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        assertThat(toSave.getLastSentAt()).isNotNull()
                .isBetween(Instant.now().minusSeconds(5), Instant.now())
                .isEqualTo(toSave.getExpiresAt().minus(7, ChronoUnit.DAYS));
        assertThat(toSave.getInvitedByName()).isEqualTo("Owner");

        verify(eventPublisher).publishEvent(new InviteEmailRequestedEvent(saved.getId(), "en"));
    }

    @Test
    @DisplayName("sendInvite_shouldRequestEmailInRequestedLanguage_whenLanguageIsSr")
    void sendInvite_shouldRequestEmailInRequestedLanguage_whenLanguageIsSr() {
        String targetEmail = "novi@example.com";
        OrgInviteModel saved = pendingInvite(orgId, targetEmail, ownerId);
        stubSendInviteHappyPath(targetEmail, saved);

        service.sendInvite(orgId, ownerId, new SendInviteRequest(targetEmail, "sr"));

        verify(eventPublisher).publishEvent(new InviteEmailRequestedEvent(saved.getId(), "sr"));
    }

    @Test
    @DisplayName("sendInvite_shouldRequestEmailInEnglish_whenLanguageIsEn")
    void sendInvite_shouldRequestEmailInEnglish_whenLanguageIsEn() {
        String targetEmail = "new@example.com";
        OrgInviteModel saved = pendingInvite(orgId, targetEmail, ownerId);
        stubSendInviteHappyPath(targetEmail, saved);

        service.sendInvite(orgId, ownerId, new SendInviteRequest(targetEmail, "en"));

        verify(eventPublisher).publishEvent(new InviteEmailRequestedEvent(saved.getId(), "en"));
    }

    @Test
    @DisplayName("sendInvite_shouldUseUnknownInviterName_whenCallerHasNoUserRow")
    void sendInvite_shouldUseUnknownInviterName_whenCallerHasNoUserRow() {
        String targetEmail = "new@example.com";
        OrgInviteModel saved = pendingInvite(orgId, targetEmail, ownerId);
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findByEmail(targetEmail)).thenReturn(Optional.empty());
        when(orgInviteRepository.existsPendingByEmailAndOrgId(targetEmail, orgId)).thenReturn(false);
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());
        when(orgInviteRepository.save(any())).thenReturn(saved);

        service.sendInvite(orgId, ownerId, new SendInviteRequest(targetEmail, null));

        ArgumentCaptor<OrgInviteModel> captor = ArgumentCaptor.forClass(OrgInviteModel.class);
        verify(orgInviteRepository).save(captor.capture());
        assertThat(captor.getValue().getInvitedByName()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("sendInvite_shouldThrowForbidden_whenCallerIsMember")
    void sendInvite_shouldThrowForbidden_whenCallerIsMember() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.sendInvite(orgId, memberId, new SendInviteRequest("x@x.com", null)))
                .isInstanceOf(ForbiddenException.class);

        verify(orgInviteRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("sendInvite_shouldThrowForbidden_whenCallerNotMember")
    void sendInvite_shouldThrowForbidden_whenCallerNotMember() {
        UUID outsider = UUID.randomUUID();
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, outsider)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendInvite(orgId, outsider, new SendInviteRequest("x@x.com", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("sendInvite_shouldThrowConflict_whenEmailAlreadyMember")
    void sendInvite_shouldThrowConflict_whenEmailAlreadyMember() {
        UUID existingUserId = UUID.randomUUID();
        UserModel existingUser = UserModel.builder().id(existingUserId).email("existing@example.com").build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
        when(organisationRepository.existsMember(orgId, existingUserId)).thenReturn(true);

        assertThatThrownBy(() -> service.sendInvite(orgId, ownerId, new SendInviteRequest("existing@example.com", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("sendInvite_shouldThrowConflict_whenPendingInviteExists")
    void sendInvite_shouldThrowConflict_whenPendingInviteExists() {
        String targetEmail = "pending@example.com";
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findByEmail(targetEmail)).thenReturn(Optional.empty());
        when(orgInviteRepository.existsPendingByEmailAndOrgId(targetEmail, orgId)).thenReturn(true);

        assertThatThrownBy(() -> service.sendInvite(orgId, ownerId, new SendInviteRequest(targetEmail, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("sendInvite_shouldThrowNotFound_whenOrgDoesNotExist")
    void sendInvite_shouldThrowNotFound_whenOrgDoesNotExist() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendInvite(orgId, ownerId, new SendInviteRequest("x@x.com", null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── listPendingInvites ───────────────────────────────────────────────────

    @Test
    @DisplayName("listPendingInvites_shouldReturnList_whenCallerIsOwner")
    void listPendingInvites_shouldReturnList_whenCallerIsOwner() {
        OrgInviteModel invite = pendingInvite(orgId, "a@a.com", ownerId);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgInviteRepository.findPendingByOrgId(orgId)).thenReturn(List.of(invite));

        List<OrgInviteModel> result = service.listPendingInvites(orgId, ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("a@a.com");
    }

    @Test
    @DisplayName("listPendingInvites_shouldThrowForbidden_whenCallerIsMember")
    void listPendingInvites_shouldThrowForbidden_whenCallerIsMember() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.listPendingInvites(orgId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    // ─── revokeInvite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeInvite_shouldMarkRevoked_whenOwnerRevokes")
    void revokeInvite_shouldMarkRevoked_whenOwnerRevokes() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        service.revokeInvite(orgId, ownerId, inviteId);

        verify(orgInviteRepository).markRevoked(inviteId);
    }

    @Test
    @DisplayName("revokeInvite_shouldThrowBadRequest_whenInviteAlreadyRevoked")
    void revokeInvite_shouldThrowBadRequest_whenInviteAlreadyRevoked() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel revoked = OrgInviteModel.builder()
                .id(inviteId).organisationId(orgId).email("a@a.com")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.revokeInvite(orgId, ownerId, inviteId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("revokeInvite_shouldThrowNotFound_whenInviteBelongsToDifferentOrg")
    void revokeInvite_shouldThrowNotFound_whenInviteBelongsToDifferentOrg() {
        UUID inviteId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, otherOrgId, "a@a.com", ownerId);

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.revokeInvite(orgId, ownerId, inviteId))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── resend ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resend_shouldUpdateLastSentAtAndRequestEmail_whenCooldownOver")
    void resend_shouldUpdateLastSentAtAndRequestEmail_whenCooldownOver() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        invite.setLastSentAt(Instant.now().minus(6, ChronoUnit.MINUTES));
        String token = invite.getToken();
        Instant expiresAt = invite.getExpiresAt();
        stubResend(inviteId, invite);
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        OrgInviteModel result = service.resend(orgId, inviteId, "sr", ownerId);

        ArgumentCaptor<Instant> sentAt = ArgumentCaptor.forClass(Instant.class);
        verify(orgInviteRepository).updateLastSentAt(eq(inviteId), sentAt.capture());
        assertThat(sentAt.getValue()).isBetween(before, Instant.now())
                .isEqualTo(sentAt.getValue().truncatedTo(ChronoUnit.MICROS));
        assertThat(result.getLastSentAt()).isEqualTo(sentAt.getValue());
        assertThat(result.getToken()).isEqualTo(token);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        verify(eventPublisher).publishEvent(new InviteEmailRequestedEvent(inviteId, "sr"));
    }

    @Test
    @DisplayName("resend_shouldRequestEmailInEnglish_whenLanguageMissing")
    void resend_shouldRequestEmailInEnglish_whenLanguageMissing() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        invite.setLastSentAt(Instant.now().minus(1, ChronoUnit.HOURS));
        stubResend(inviteId, invite);

        service.resend(orgId, inviteId, null, ownerId);

        verify(eventPublisher).publishEvent(new InviteEmailRequestedEvent(inviteId, "en"));
    }

    @Test
    @DisplayName("resend_shouldRequestEmail_whenInviteWasNeverSent")
    void resend_shouldRequestEmail_whenInviteWasNeverSent() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        stubResend(inviteId, invite);

        service.resend(orgId, inviteId, "en", ownerId);

        verify(orgInviteRepository).updateLastSentAt(eq(inviteId), any(Instant.class));
        verify(eventPublisher).publishEvent(new InviteEmailRequestedEvent(inviteId, "en"));
    }

    @Test
    @DisplayName("resend_shouldAllowAdmin")
    void resend_shouldAllowAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, adminId)).thenReturn(Optional.of(OrganisationRole.ADMIN));
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));

        service.resend(orgId, inviteId, "en", adminId);

        verify(eventPublisher).publishEvent(new InviteEmailRequestedEvent(inviteId, "en"));
    }

    @Test
    @DisplayName("resend_shouldThrowTooManyRequestsWithSecondsLeft_whenInsideCooldown")
    void resend_shouldThrowTooManyRequestsWithSecondsLeft_whenInsideCooldown() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        invite.setLastSentAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        stubResend(inviteId, invite);

        assertThatThrownBy(() -> service.resend(orgId, inviteId, "en", ownerId))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(ErrorMessages.OrgInvite.RESEND_COOLDOWN)
                .extracting(e -> ((TooManyRequestsException) e).getRetryAfterSeconds())
                .satisfies(seconds -> assertThat((long) seconds).isBetween(235L, 240L));

        verify(orgInviteRepository, never()).updateLastSentAt(any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("resend_shouldRoundRetryAfterUpToOneSecond_whenCooldownAlmostOver")
    void resend_shouldRoundRetryAfterUpToOneSecond_whenCooldownAlmostOver() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        invite.setLastSentAt(Instant.now().minus(OrgInviteService.RESEND_COOLDOWN).plusMillis(500));
        stubResend(inviteId, invite);

        assertThatThrownBy(() -> service.resend(orgId, inviteId, "en", ownerId))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting(e -> ((TooManyRequestsException) e).getRetryAfterSeconds())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("resend_shouldThrowConflict_whenInviteRevoked")
    void resend_shouldThrowConflict_whenInviteRevoked() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        invite.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        assertResendConflict(inviteId, invite);
    }

    @Test
    @DisplayName("resend_shouldThrowConflict_whenInviteAccepted")
    void resend_shouldThrowConflict_whenInviteAccepted() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        invite.setAcceptedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        assertResendConflict(inviteId, invite);
    }

    @Test
    @DisplayName("resend_shouldThrowConflict_whenInviteExpired")
    void resend_shouldThrowConflict_whenInviteExpired() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, orgId, "a@a.com", ownerId);
        invite.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        assertResendConflict(inviteId, invite);
    }

    @Test
    @DisplayName("resend_shouldThrowNotFound_whenInviteBelongsToDifferentOrg")
    void resend_shouldThrowNotFound_whenInviteBelongsToDifferentOrg() {
        UUID inviteId = UUID.randomUUID();
        OrgInviteModel invite = pendingInvite(inviteId, UUID.randomUUID(), "a@a.com", ownerId);
        stubResend(inviteId, invite);

        assertThatThrownBy(() -> service.resend(orgId, inviteId, "en", ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.OrgInvite.NOT_FOUND);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("resend_shouldThrowNotFound_whenInviteDoesNotExist")
    void resend_shouldThrowNotFound_whenInviteDoesNotExist() {
        UUID inviteId = UUID.randomUUID();
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resend(orgId, inviteId, "en", ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("resend_shouldThrowForbidden_whenCallerIsMember")
    void resend_shouldThrowForbidden_whenCallerIsMember() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.resend(orgId, UUID.randomUUID(), "en", memberId))
                .isInstanceOf(ForbiddenException.class);
        verify(orgInviteRepository, never()).updateLastSentAt(any(), any());
    }

    @Test
    @DisplayName("resend_shouldThrowNotFound_whenOrgDoesNotExist")
    void resend_shouldThrowNotFound_whenOrgDoesNotExist() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resend(orgId, UUID.randomUUID(), "en", ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_FOUND);
    }

    // ─── acceptInvite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptInvite_shouldAddMemberAndMarkAccepted_whenTokenValid")
    void acceptInvite_shouldAddMemberAndMarkAccepted_whenTokenValid() {
        String token = "abc123";
        UUID inviteId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        String email = "invited@example.com";

        OrgInviteModel invite = pendingInvite(inviteId, orgId, email, ownerId);
        invite = OrgInviteModel.builder()
                .id(inviteId).organisationId(orgId).email(email)
                .invitedBy(ownerId).token(token)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        UserModel newUser = UserModel.builder().id(newUserId).email(email).name("Invited").build();
        OrgInviteModel accepted = OrgInviteModel.builder()
                .id(inviteId).organisationId(orgId).email(email)
                .acceptedAt(Instant.now()).expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(orgInviteRepository.findByToken(token)).thenReturn(Optional.of(invite));
        when(userRepository.findById(newUserId)).thenReturn(Optional.of(newUser));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(newUser));
        when(organisationRepository.existsMember(orgId, newUserId)).thenReturn(false);
        when(organisationRepository.findByMember(newUserId)).thenReturn(Optional.empty());
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(accepted));

        OrgInviteModel result = service.acceptInvite(token, newUserId);

        verify(organisationRepository).saveMember(orgId, newUserId, OrganisationRole.MEMBER);
        verify(orgInviteRepository).markAccepted(inviteId);
        assertThat(result.getAcceptedAt()).isNotNull();
        verify(eventPublisher).publishEvent(new InviteAcceptedEvent(inviteId, orgId, newUserId, ownerId));
    }

    // JOB-392: one organisation per user, as on create (JOB-241)
    @Test
    @DisplayName("acceptInvite_shouldThrowConflict_whenUserBelongsToAnotherOrganisation")
    void acceptInvite_shouldThrowConflict_whenUserBelongsToAnotherOrganisation() {
        String token = "abc123";
        UUID inviteId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        String email = "invited@example.com";
        OrgInviteModel invite = pendingInvite(inviteId, orgId, email, ownerId);
        UserModel caller = UserModel.builder().id(callerId).email(email).name("Invited").build();
        OrganisationModel otherOrg = OrganisationModel.builder()
                .id(UUID.randomUUID()).name("Pine Studio").slug("PIN").createdBy(callerId).build();

        when(orgInviteRepository.findByToken(token)).thenReturn(Optional.of(invite));
        when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(caller));
        when(organisationRepository.existsMember(orgId, callerId)).thenReturn(false);
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(otherOrg));

        assertThatThrownBy(() -> service.acceptInvite(token, callerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Organisation.ALREADY_IN_ORGANISATION);
        verify(organisationRepository, never()).saveMember(any(), any(), any());
        verify(orgInviteRepository, never()).markAccepted(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("acceptInvite_shouldThrowForbidden_whenEmailDoesNotMatch")
    void acceptInvite_shouldThrowForbidden_whenEmailDoesNotMatch() {
        String token = "abc123";
        OrgInviteModel invite = OrgInviteModel.builder()
                .id(UUID.randomUUID()).organisationId(orgId).email("someone@example.com")
                .token(token).expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        UserModel caller = UserModel.builder().id(memberId).email("different@example.com").build();

        when(orgInviteRepository.findByToken(token)).thenReturn(Optional.of(invite));
        when(userRepository.findById(memberId)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> service.acceptInvite(token, memberId))
                .isInstanceOf(ForbiddenException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("acceptInvite_shouldThrowNotFound_whenTokenDoesNotExist")
    void acceptInvite_shouldThrowNotFound_whenTokenDoesNotExist() {
        when(orgInviteRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvite("bad-token", memberId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("acceptInvite_shouldThrowNotFound_whenCallerUserDoesNotExist")
    void acceptInvite_shouldThrowNotFound_whenCallerUserDoesNotExist() {
        String token = "abc123";
        OrgInviteModel invite = OrgInviteModel.builder()
                .id(UUID.randomUUID()).organisationId(orgId).email("invited@example.com")
                .token(token).expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(orgInviteRepository.findByToken(token)).thenReturn(Optional.of(invite));
        when(userRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvite(token, memberId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("acceptInvite_shouldThrowBadRequest_whenInviteExpired")
    void acceptInvite_shouldThrowBadRequest_whenInviteExpired() {
        String token = "expired-token";
        OrgInviteModel expired = OrgInviteModel.builder()
                .id(UUID.randomUUID()).organisationId(orgId).email("x@x.com")
                .token(token).expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(orgInviteRepository.findByToken(token)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.acceptInvite(token, memberId))
                .isInstanceOf(BadRequestException.class);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void stubSendInviteHappyPath(String targetEmail, OrgInviteModel saved) {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(userRepository.findByEmail(targetEmail)).thenReturn(Optional.empty());
        when(orgInviteRepository.existsPendingByEmailAndOrgId(targetEmail, orgId)).thenReturn(false);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(orgInviteRepository.save(any())).thenReturn(saved);
    }

    private void stubResend(UUID inviteId, OrgInviteModel invite) {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));
    }

    private void assertResendConflict(UUID inviteId, OrgInviteModel invite) {
        stubResend(inviteId, invite);

        assertThatThrownBy(() -> service.resend(orgId, inviteId, "en", ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.OrgInvite.INVALID);
        verify(orgInviteRepository, never()).updateLastSentAt(any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private OrgInviteModel pendingInvite(UUID orgId, String email, UUID invitedBy) {
        return pendingInvite(UUID.randomUUID(), orgId, email, invitedBy);
    }

    private OrgInviteModel pendingInvite(UUID id, UUID orgId, String email, UUID invitedBy) {
        return OrgInviteModel.builder()
                .id(id)
                .organisationId(orgId)
                .email(email)
                .invitedBy(invitedBy)
                .token("token-" + id)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }
}
