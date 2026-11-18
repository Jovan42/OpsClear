package com.opsclear.service;

import com.opsclear.dto.UpdateOrgSettingsRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.OrgSettingsModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.repository.OrgSettingsRepository;
import com.opsclear.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads and changes the org's four friendly-ID prefixes (ADR-0060). A change applies to new records
 * only: existing records keep their stored friendly ID, and {@link FriendlyIdService} reads the
 * current prefix each time it generates one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgSettingsService {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[A-Z]{2,3}$");

    private final OrgSettingsRepository orgSettingsRepository;
    private final OrganisationRepository organisationRepository;

    @Transactional(readOnly = true)
    public OrgSettingsModel get(UUID orgId, UUID callerId) {
        requireOrg(orgId);
        requireMember(orgId, callerId);
        return withNextIds(requireSettings(orgId));
    }

    @Transactional
    public OrgSettingsModel update(UUID orgId, UpdateOrgSettingsRequest request, UUID callerId) {
        requireOrg(orgId);
        requireOwner(orgId, callerId);
        OrgSettingsModel current = requireSettings(orgId);

        Map<FriendlyIdEntityType, String> requested = requestedPrefixes(request);
        Map<FriendlyIdEntityType, String> resulting = new EnumMap<>(FriendlyIdEntityType.class);
        for (FriendlyIdEntityType type : FriendlyIdEntityType.values()) {
            resulting.put(type, requested.getOrDefault(type, current.getPrefix(type)));
        }
        requireDistinct(resulting);

        List<FriendlyIdEntityType> changed = resulting.keySet().stream()
                .filter(type -> !resulting.get(type).equals(current.getPrefix(type)))
                .toList();
        if (changed.isEmpty()) {
            return withNextIds(current);
        }
        for (FriendlyIdEntityType type : changed) {
            requirePrefixNotUsedByOtherType(orgId, resulting.get(type), type);
        }

        OrgSettingsModel updated = orgSettingsRepository.update(OrgSettingsModel.builder()
                .orgId(orgId)
                .projectPrefix(resulting.get(FriendlyIdEntityType.PROJECT))
                .jobPrefix(resulting.get(FriendlyIdEntityType.JOB))
                .milestonePrefix(resulting.get(FriendlyIdEntityType.MILESTONE))
                .templatePrefix(resulting.get(FriendlyIdEntityType.TEMPLATE))
                .build());

        String diff = changed.stream()
                .map(type -> type + " " + current.getPrefix(type) + " → " + resulting.get(type))
                .collect(Collectors.joining(", "));
        log.info("Updated ID prefixes for organisation {} by user {}: {}", orgId, callerId, diff);
        return withNextIds(updated);
    }

    private Map<FriendlyIdEntityType, String> requestedPrefixes(UpdateOrgSettingsRequest request) {
        Map<FriendlyIdEntityType, String> requested = new EnumMap<>(FriendlyIdEntityType.class);
        putIfPresent(requested, FriendlyIdEntityType.PROJECT, request.getProjectPrefix());
        putIfPresent(requested, FriendlyIdEntityType.JOB, request.getJobPrefix());
        putIfPresent(requested, FriendlyIdEntityType.MILESTONE, request.getMilestonePrefix());
        putIfPresent(requested, FriendlyIdEntityType.TEMPLATE, request.getTemplatePrefix());
        return requested;
    }

    private void putIfPresent(Map<FriendlyIdEntityType, String> target, FriendlyIdEntityType type, String raw) {
        if (raw != null) {
            target.put(type, normalise(raw));
        }
    }

    private String normalise(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!PREFIX_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(String.format(ErrorMessages.OrgSettings.INVALID_PREFIX, raw.trim()));
        }
        return value;
    }

    private OrgSettingsModel withNextIds(OrgSettingsModel settings) {
        Map<FriendlyIdEntityType, Integer> lastValues = orgSettingsRepository.findLastValues(settings.getOrgId());
        for (FriendlyIdEntityType type : FriendlyIdEntityType.values()) {
            int next = lastValues.getOrDefault(type, 0) + 1;
            settings.getNextIds().put(type, FriendlyIdService.format(settings.getPrefix(type), next));
        }
        return settings;
    }

    private static String pluralLabel(FriendlyIdEntityType type) {
        return switch (type) {
            case PROJECT -> "projects";
            case JOB -> "jobs";
            case MILESTONE -> "milestones";
            case TEMPLATE -> "templates";
        };
    }

    // --- Guards ---

    private void requireOrg(UUID orgId) {
        organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private OrgSettingsModel requireSettings(UUID orgId) {
        return orgSettingsRepository.findByOrgId(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.OrgSettings.NOT_FOUND));
    }

    private void requireMember(UUID orgId, UUID userId) {
        organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER));
    }

    private void requireOwner(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER));
        if (role != OrganisationRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER);
        }
    }

    private void requireDistinct(Map<FriendlyIdEntityType, String> prefixes) {
        if (new HashSet<>(prefixes.values()).size() != prefixes.size()) {
            throw new BadRequestException(ErrorMessages.OrgSettings.PREFIXES_NOT_DISTINCT);
        }
    }

    private void requirePrefixNotUsedByOtherType(UUID orgId, String prefix, FriendlyIdEntityType type) {
        orgSettingsRepository.findOtherTypeUsingPrefix(orgId, prefix, type).ifPresent(other -> {
            throw new BadRequestException(String.format(
                    ErrorMessages.OrgSettings.PREFIX_USED_BY_OTHER_TYPE, prefix, pluralLabel(other)));
        });
    }
}
