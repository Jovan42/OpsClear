package com.opsclear.service;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves path parameters that may be either a UUID or a human-friendly ID (e.g. PRJ-001).
 * Resolution is always scoped to the caller's organisation — a friendly ID from another org
 * resolves to 404, not the wrong record.
 */
@Component
@RequiredArgsConstructor
public class FriendlyIdResolver {

    private static final Pattern FRIENDLY_ID_PATTERN =
            Pattern.compile("^[A-Za-z]{2,6}-\\d+$");

    private final FriendlyIdRepository friendlyIdRepository;
    private final OrganisationRepository organisationRepository;

    @Transactional(readOnly = true)
    public UUID resolveProject(String param, UUID callerId) {
        if (isUuid(param)) return UUID.fromString(param);
        UUID orgId = requireOrgId(callerId);
        return friendlyIdRepository.findProjectId(param, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UUID resolveJob(String param, UUID callerId) {
        if (isUuid(param)) return UUID.fromString(param);
        UUID orgId = requireOrgId(callerId);
        return friendlyIdRepository.findJobId(param, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UUID resolveMilestone(String param, UUID callerId) {
        if (isUuid(param)) return UUID.fromString(param);
        UUID orgId = requireOrgId(callerId);
        return friendlyIdRepository.findMilestoneId(param, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Milestone.NOT_FOUND));
    }

    static boolean isFriendlyId(String param) {
        return FRIENDLY_ID_PATTERN.matcher(param).matches();
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private UUID requireOrgId(UUID callerId) {
        return organisationRepository.findByMember(callerId)
                .map(OrganisationModel::getId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_IN_ORG));
    }
}
