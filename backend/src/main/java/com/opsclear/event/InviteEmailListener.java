package com.opsclear.event;

import com.opsclear.config.AsyncConfig;
import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.service.EmailService;
import com.opsclear.service.InviteEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;
import java.util.Optional;

/**
 * Sends the invite email (ADR-0059). Runs only after the invite's transaction commits, so a rolled-back invite never
 * sends anything, and on the notification executor, so SMTP never slows the request. {@link EmailService} swallows
 * and logs send failures; owners/admins can always copy the link instead.
 *
 * <p>The invite is reloaded here: if it's gone or no longer pending by the time the send runs, nothing is sent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InviteEmailListener {

    private final OrgInviteRepository orgInviteRepository;
    private final OrganisationRepository organisationRepository;
    private final EmailService emailService;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteEmailRequested(InviteEmailRequestedEvent event) {
        Optional<OrgInviteModel> invite = orgInviteRepository.findById(event.inviteId())
                .filter(OrgInviteModel::isPending);
        if (invite.isEmpty()) {
            log.info("Invite {} is no longer pending: no invite email sent", event.inviteId());
            return;
        }
        Optional<OrganisationModel> org = organisationRepository
                .findByIdAndDeletedAtIsNull(invite.get().getOrganisationId());
        if (org.isEmpty()) {
            log.info("Organisation of invite {} is gone: no invite email sent", event.inviteId());
            return;
        }
        emailService.sendInvite(toEmail(invite.get(), org.get(), event.language()));
    }

    private static InviteEmail toEmail(OrgInviteModel invite, OrganisationModel org, String language) {
        // invited_by_name comes from a LEFT JOIN, so it's null if the inviter's user row is gone
        String inviterName = Objects.requireNonNullElse(invite.getInvitedByName(), org.getName());
        return new InviteEmail(invite.getId(), invite.getEmail(), language, org.getName(), inviterName,
                invite.getToken(), invite.getExpiresAt());
    }
}
