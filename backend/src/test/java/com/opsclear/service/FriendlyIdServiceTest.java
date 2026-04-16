package com.opsclear.service;

import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.repository.FriendlyIdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendlyIdService")
class FriendlyIdServiceTest {

    @Mock
    private FriendlyIdRepository friendlyIdRepository;

    private FriendlyIdService friendlyIdService;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        friendlyIdService = new FriendlyIdService(friendlyIdRepository);
        orgId = UUID.randomUUID();
    }

    @Test
    @DisplayName("nextFriendlyId — formats JOB-001 for first job")
    void nextFriendlyId_shouldFormatCorrectly_forFirstJob() {
        when(friendlyIdRepository.getPrefix(orgId, FriendlyIdEntityType.JOB)).thenReturn("JOB");
        when(friendlyIdRepository.incrementAndGet(orgId, FriendlyIdEntityType.JOB)).thenReturn(1);

        String result = friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.JOB);

        assertThat(result).isEqualTo("JOB-001");
    }

    @Test
    @DisplayName("nextFriendlyId — formats PRJ-042 for 42nd project")
    void nextFriendlyId_shouldFormatCorrectly_forHigherSequenceValue() {
        when(friendlyIdRepository.getPrefix(orgId, FriendlyIdEntityType.PROJECT)).thenReturn("PRJ");
        when(friendlyIdRepository.incrementAndGet(orgId, FriendlyIdEntityType.PROJECT)).thenReturn(42);

        String result = friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.PROJECT);

        assertThat(result).isEqualTo("PRJ-042");
    }

    @Test
    @DisplayName("nextFriendlyId — pads to 3 digits for values below 10")
    void nextFriendlyId_shouldPadToThreeDigits_forSingleDigitValue() {
        when(friendlyIdRepository.getPrefix(orgId, FriendlyIdEntityType.MILESTONE)).thenReturn("MIL");
        when(friendlyIdRepository.incrementAndGet(orgId, FriendlyIdEntityType.MILESTONE)).thenReturn(7);

        String result = friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.MILESTONE);

        assertThat(result).isEqualTo("MIL-007");
    }

    @Test
    @DisplayName("nextFriendlyId — does not pad values above 999")
    void nextFriendlyId_shouldNotPad_forLargeValues() {
        when(friendlyIdRepository.getPrefix(orgId, FriendlyIdEntityType.JOB)).thenReturn("JOB");
        when(friendlyIdRepository.incrementAndGet(orgId, FriendlyIdEntityType.JOB)).thenReturn(1000);

        String result = friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.JOB);

        assertThat(result).isEqualTo("JOB-1000");
    }

    @Test
    @DisplayName("nextFriendlyId — uses custom prefix when org has configured one")
    void nextFriendlyId_shouldUseCustomPrefix_whenOrgHasConfiguredOne() {
        when(friendlyIdRepository.getPrefix(orgId, FriendlyIdEntityType.JOB)).thenReturn("TSK");
        when(friendlyIdRepository.incrementAndGet(orgId, FriendlyIdEntityType.JOB)).thenReturn(5);

        String result = friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.JOB);

        assertThat(result).isEqualTo("TSK-005");
    }

    @Test
    @DisplayName("seedForOrg — delegates to repository")
    void seedForOrg_shouldDelegateToRepository() {
        friendlyIdService.seedForOrg(orgId);

        verify(friendlyIdRepository).seedForOrg(orgId);
    }
}
