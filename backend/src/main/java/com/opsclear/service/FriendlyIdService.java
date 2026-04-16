package com.opsclear.service;

import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.repository.FriendlyIdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendlyIdService {

    private final FriendlyIdRepository friendlyIdRepository;

    /**
     * Generates the next friendly ID for an entity within an org (e.g. "JOB-042").
     * Must be called inside an existing transaction so the sequence increment and entity
     * insert are committed atomically.
     */
    @Transactional
    public String nextFriendlyId(UUID orgId, FriendlyIdEntityType entityType) {
        String prefix = friendlyIdRepository.getPrefix(orgId, entityType);
        int value = friendlyIdRepository.incrementAndGet(orgId, entityType);
        String id = format(prefix, value);
        log.debug("Generated friendly ID {} for org {} type {}", id, orgId, entityType);
        return id;
    }

    /**
     * Seeds org_settings and org_sequences for a newly created org.
     * Must be called inside the same transaction as org creation.
     */
    @Transactional
    public void seedForOrg(UUID orgId) {
        friendlyIdRepository.seedForOrg(orgId);
        log.debug("Seeded friendly ID settings for org {}", orgId);
    }

    private String format(String prefix, int value) {
        return prefix + "-" + String.format("%03d", value);
    }
}
