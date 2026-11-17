package com.opsclear.event;

import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.service.EmailService;
import com.opsclear.service.InviteEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteEmailListener")
class InviteEmailListenerTest {

    @Mock private OrgInviteRepository orgInviteRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private EmailService emailService;

    private InviteEmailListener listener;
    private UUID inviteId;
    private UUID orgId;
    private Instant expiresAt;
    private OrganisationModel org;

    @BeforeEach
    void setUp() {
        listener = new InviteEmailListener(orgInviteRepository, organisationRepository, emailService);
        inviteId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        org = OrganisationModel.builder().id(orgId).name("Acme Corp").slug("ACM").build();
    }

    @Test
    @DisplayName("onInviteEmailRequested_shouldSendEmailWithInviteOrgAndInviter_whenInvitePending")
    void onInviteEmailRequested_shouldSendEmailWithInviteOrgAndInviter_whenInvitePending() {
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite("Olivia Owner", null)));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));

        listener.onInviteEmailRequested(new InviteEmailRequestedEvent(inviteId, "sr"));

        verify(emailService).sendInvite(new InviteEmail(inviteId, "invitee@example.com", "sr", "Acme Corp",
                "Olivia Owner", "tok", expiresAt));
    }

    @Test
    @DisplayName("onInviteEmailRequested_shouldUseOrgNameAsInviter_whenInviterNameMissing")
    void onInviteEmailRequested_shouldUseOrgNameAsInviter_whenInviterNameMissing() {
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite(null, null)));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));

        listener.onInviteEmailRequested(new InviteEmailRequestedEvent(inviteId, "en"));

        verify(emailService).sendInvite(new InviteEmail(inviteId, "invitee@example.com", "en", "Acme Corp",
                "Acme Corp", "tok", expiresAt));
    }

    @Test
    @DisplayName("onInviteEmailRequested_shouldSendNothing_whenInviteNotFound")
    void onInviteEmailRequested_shouldSendNothing_whenInviteNotFound() {
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.empty());

        listener.onInviteEmailRequested(new InviteEmailRequestedEvent(inviteId, "en"));

        verify(emailService, never()).sendInvite(any());
    }

    @Test
    @DisplayName("onInviteEmailRequested_shouldSendNothing_whenInviteRevoked")
    void onInviteEmailRequested_shouldSendNothing_whenInviteRevoked() {
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite("Olivia Owner", Instant.now())));

        listener.onInviteEmailRequested(new InviteEmailRequestedEvent(inviteId, "en"));

        verify(emailService, never()).sendInvite(any());
        verify(organisationRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("onInviteEmailRequested_shouldSendNothing_whenOrganisationDeleted")
    void onInviteEmailRequested_shouldSendNothing_whenOrganisationDeleted() {
        when(orgInviteRepository.findById(inviteId)).thenReturn(Optional.of(invite("Olivia Owner", null)));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        listener.onInviteEmailRequested(new InviteEmailRequestedEvent(inviteId, "en"));

        verify(emailService, never()).sendInvite(any());
    }

    private OrgInviteModel invite(String inviterName, Instant revokedAt) {
        return OrgInviteModel.builder()
                .id(inviteId)
                .organisationId(orgId)
                .email("invitee@example.com")
                .invitedByName(inviterName)
                .token("tok")
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }
}
