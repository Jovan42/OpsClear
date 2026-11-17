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
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrgInviteService {

    static final String DEFAULT_LANGUAGE = "en";
    private static final int INVITE_EXPIRY_DAYS = 7;
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(5);
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OrgInviteRepository orgInviteRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrgInviteModel sendInvite(UUID orgId, UUID callerId, SendInviteRequest request) {
        requireOrgExists(orgId);
        requireOwnerOrAdmin(orgId, callerId);
        requireEmailNotAlreadyMember(orgId, request.getEmail());
        requireNoPendingInvite(orgId, request.getEmail());

        String callerName = userRepository.findById(callerId)
                .map(UserModel::getName)
                .orElse("Unknown");

        String token = generateToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(INVITE_EXPIRY_DAYS, ChronoUnit.DAYS);

        OrgInviteModel invite = OrgInviteModel.builder()
                .organisationId(orgId)
                .email(request.getEmail())
                .invitedBy(callerId)
                .invitedByName(callerName)
                .token(token)
                .expiresAt(expiresAt)
                .lastSentAt(now)
                .build();

        OrgInviteModel saved = orgInviteRepository.save(invite);
        log.info("Invite {} created for organisation {} by {}", saved.getId(), orgId, callerId);
        // Sent after commit, asynchronously (ADR-0059): a failed send never fails the invite
        eventPublisher.publishEvent(
                new InviteEmailRequestedEvent(saved.getId(), resolveLanguage(request.getLanguage())));

        return saved;
    }

    // Sends the invite email again (ADR-0059). The token and expiry stay the same.
    @Transactional
    public OrgInviteModel resend(UUID orgId, UUID inviteId, String language, UUID callerId) {
        requireOrgExists(orgId);
        requireOwnerOrAdmin(orgId, callerId);

        OrgInviteModel invite = requireInviteRecord(orgId, inviteId);
        requireInvitePendingForResend(invite);
        // Postgres keeps microseconds: truncate so the response matches the stored value
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        requireResendCooldownOver(invite, now);

        orgInviteRepository.updateLastSentAt(inviteId, now);
        invite.setLastSentAt(now);
        log.info("Invite {} email resent by {} in organisation {}", inviteId, callerId, orgId);
        eventPublisher.publishEvent(new InviteEmailRequestedEvent(inviteId, resolveLanguage(language)));

        return invite;
    }

    @Transactional(readOnly = true)
    public List<OrgInviteModel> listPendingInvites(UUID orgId, UUID callerId) {
        requireOrgExists(orgId);
        requireOwnerOrAdmin(orgId, callerId);
        return orgInviteRepository.findPendingByOrgId(orgId);
    }

    @Transactional
    public void revokeInvite(UUID orgId, UUID callerId, UUID inviteId) {
        requireOrgExists(orgId);
        requireOwnerOrAdmin(orgId, callerId);

        OrgInviteModel invite = requireInviteRecord(orgId, inviteId);
        requireInvitePending(invite);

        orgInviteRepository.markRevoked(inviteId);
        log.info("Invite {} revoked by {} in organisation {}", inviteId, callerId, orgId);
    }

    @Transactional
    public OrgInviteModel acceptInvite(String token, UUID callerId) {
        OrgInviteModel invite = orgInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.OrgInvite.NOT_FOUND));
        requireInvitePending(invite);

        String callerEmail = userRepository.findById(callerId)
                .map(UserModel::getEmail)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));

        requireEmailMatchesInvite(invite.getEmail(), callerEmail);
        requireEmailNotAlreadyMember(invite.getOrganisationId(), callerEmail);

        organisationRepository.saveMember(invite.getOrganisationId(), callerId, OrganisationRole.MEMBER);
        orgInviteRepository.markAccepted(invite.getId());
        log.info("Invite {} accepted by user {} ({})", invite.getId(), callerId, callerEmail);
        eventPublisher.publishEvent(new InviteAcceptedEvent(
                invite.getId(), invite.getOrganisationId(), callerId, invite.getInvitedBy()));

        return orgInviteRepository.findById(invite.getId()).orElseThrow();
    }

    // DTO validation allows only en/sr; a missing language means en
    private static String resolveLanguage(String language) {
        return language != null ? language : DEFAULT_LANGUAGE;
    }

    // --- Business rule guards ---

    private void requireEmailNotAlreadyMember(UUID orgId, String email) {
        boolean alreadyMember = userRepository.findByEmail(email)
                .map(u -> organisationRepository.existsMember(orgId, u.getId()))
                .orElse(false);
        if (alreadyMember) {
            throw new ConflictException(ErrorMessages.OrgInvite.EMAIL_ALREADY_MEMBER);
        }
    }

    private void requireNoPendingInvite(UUID orgId, String email) {
        if (orgInviteRepository.existsPendingByEmailAndOrgId(email, orgId)) {
            throw new ConflictException(ErrorMessages.OrgInvite.ALREADY_PENDING);
        }
    }

    private void requireInvitePending(OrgInviteModel invite) {
        if (!invite.isPending()) {
            throw new BadRequestException(ErrorMessages.OrgInvite.INVALID);
        }
    }

    // Resend answers 409 for an invite that's accepted, revoked or expired (ADR-0059)
    private void requireInvitePendingForResend(OrgInviteModel invite) {
        if (!invite.isPending()) {
            throw new ConflictException(ErrorMessages.OrgInvite.INVALID);
        }
    }

    // A row with no last_sent_at has never been emailed, so it has no cooldown
    private void requireResendCooldownOver(OrgInviteModel invite, Instant now) {
        if (invite.getLastSentAt() == null) {
            return;
        }
        Duration remaining = Duration.between(now, invite.getLastSentAt().plus(RESEND_COOLDOWN));
        if (remaining.isPositive()) {
            throw new TooManyRequestsException(ErrorMessages.OrgInvite.RESEND_COOLDOWN, roundUpToSeconds(remaining));
        }
    }

    private void requireEmailMatchesInvite(String inviteEmail, String callerEmail) {
        if (!inviteEmail.equalsIgnoreCase(callerEmail)) {
            throw new ForbiddenException(ErrorMessages.OrgInvite.EMAIL_MISMATCH);
        }
    }

    // --- Guards ---

    private void requireOrgExists(UUID orgId) {
        organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private OrgInviteModel requireInviteRecord(UUID orgId, UUID inviteId) {
        return orgInviteRepository.findById(inviteId)
                .filter(i -> i.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.OrgInvite.NOT_FOUND));
    }

    // --- Permission helpers ---

    private void requireOwnerOrAdmin(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER));
        if (role == OrganisationRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    // Retry-After is whole seconds: round up so a client that waits that long is past the cooldown
    private static long roundUpToSeconds(Duration duration) {
        return (duration.toNanos() + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
    }

    // --- Token generation ---

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
