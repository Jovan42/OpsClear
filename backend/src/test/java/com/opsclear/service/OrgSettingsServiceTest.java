package com.opsclear.service;

import com.opsclear.dto.UpdateOrgSettingsRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.OrgSettingsModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.repository.OrgSettingsRepository;
import com.opsclear.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrgSettingsService")
class OrgSettingsServiceTest {

    @Mock private OrgSettingsRepository orgSettingsRepository;
    @Mock private OrganisationRepository organisationRepository;

    private OrgSettingsService service;

    private UUID orgId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        service = new OrgSettingsService(orgSettingsRepository, organisationRepository);
        orgId = UUID.randomUUID();
        callerId = UUID.randomUUID();

        Map<FriendlyIdEntityType, Integer> lastValues = new EnumMap<>(FriendlyIdEntityType.class);
        lastValues.put(FriendlyIdEntityType.PROJECT, 14);
        lastValues.put(FriendlyIdEntityType.JOB, 346);
        lastValues.put(FriendlyIdEntityType.MILESTONE, 0);
        lastValues.put(FriendlyIdEntityType.TEMPLATE, 1234);
        lenient().when(orgSettingsRepository.findLastValues(orgId)).thenReturn(lastValues);
    }

    private OrgSettingsModel defaults() {
        return OrgSettingsModel.builder()
                .orgId(orgId).projectPrefix("PRJ").jobPrefix("JOB").milestonePrefix("MIL").templatePrefix("TPL")
                .build();
    }

    private void givenOrg() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
    }

    private void givenRole(OrganisationRole role) {
        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.of(role));
    }

    private void givenOwnerWithDefaults() {
        givenOrg();
        givenRole(OrganisationRole.OWNER);
        when(orgSettingsRepository.findByOrgId(orgId)).thenReturn(Optional.of(defaults()));
    }

    private void givenUpdateEchoes() {
        when(orgSettingsRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private OrgSettingsModel captureUpdate() {
        ArgumentCaptor<OrgSettingsModel> captor = ArgumentCaptor.forClass(OrgSettingsModel.class);
        verify(orgSettingsRepository).update(captor.capture());
        return captor.getValue();
    }

    // --- get ---

    @Test
    @DisplayName("get returns the prefixes and the next ID per type, formatted %03d")
    void get_shouldReturnPrefixesAndNextIds_forMember() {
        givenOrg();
        givenRole(OrganisationRole.MEMBER);
        when(orgSettingsRepository.findByOrgId(orgId)).thenReturn(Optional.of(defaults()));

        OrgSettingsModel result = service.get(orgId, callerId);

        assertThat(result.getJobPrefix()).isEqualTo("JOB");
        assertThat(result.getNextIds())
                .containsEntry(FriendlyIdEntityType.PROJECT, "PRJ-015")
                .containsEntry(FriendlyIdEntityType.JOB, "JOB-347")
                .containsEntry(FriendlyIdEntityType.MILESTONE, "MIL-001")
                .containsEntry(FriendlyIdEntityType.TEMPLATE, "TPL-1235");
    }

    @Test
    @DisplayName("get throws 404 when the organisation doesn't exist")
    void get_shouldThrowNotFound_whenOrgMissing() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(orgId, callerId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("get throws 403 for a non-member")
    void get_shouldThrowForbidden_forNonMember() {
        givenOrg();
        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(orgId, callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("get throws 404 when the org has no settings row")
    void get_shouldThrowNotFound_whenSettingsMissing() {
        givenOrg();
        givenRole(OrganisationRole.MEMBER);
        when(orgSettingsRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(orgId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organisation settings not found");
    }

    // --- update: permissions ---

    @Test
    @DisplayName("update throws 403 for an Admin")
    void update_shouldThrowForbidden_forAdmin() {
        givenOrg();
        givenRole(OrganisationRole.ADMIN);

        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("TSK").build(),
                callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("OWNER role required");
        verify(orgSettingsRepository, never()).update(any());
    }

    @Test
    @DisplayName("update throws 403 for a Member")
    void update_shouldThrowForbidden_forMember() {
        givenOrg();
        givenRole(OrganisationRole.MEMBER);

        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("TSK").build(),
                callerId))
                .isInstanceOf(ForbiddenException.class);
        verify(orgSettingsRepository, never()).update(any());
    }

    @Test
    @DisplayName("update throws 403 for a non-member")
    void update_shouldThrowForbidden_forNonMember() {
        givenOrg();
        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(orgId, new UpdateOrgSettingsRequest(), callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("update throws 404 when the organisation doesn't exist")
    void update_shouldThrowNotFound_whenOrgMissing() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(orgId, new UpdateOrgSettingsRequest(), callerId))
                .isInstanceOf(NotFoundException.class);
    }

    // --- update: normalisation and format ---

    @Test
    @DisplayName("update trims and uppercases every provided prefix")
    void update_shouldNormaliseAllPrefixes() {
        givenOwnerWithDefaults();
        givenUpdateEchoes();
        when(orgSettingsRepository.findOtherTypeUsingPrefix(eq(orgId), anyString(), any()))
                .thenReturn(Optional.empty());

        OrgSettingsModel result = service.update(orgId, UpdateOrgSettingsRequest.builder()
                .projectPrefix(" prj").jobPrefix("  tsk ").milestonePrefix("Ph")
                .templatePrefix("tmp").build(), callerId);

        OrgSettingsModel saved = captureUpdate();
        assertThat(saved.getOrgId()).isEqualTo(orgId);
        assertThat(saved.getProjectPrefix()).isEqualTo("PRJ");
        assertThat(saved.getJobPrefix()).isEqualTo("TSK");
        assertThat(saved.getMilestonePrefix()).isEqualTo("PH");
        assertThat(saved.getTemplatePrefix()).isEqualTo("TMP");
        assertThat(result.getNextIds())
                .containsEntry(FriendlyIdEntityType.JOB, "TSK-347")
                .containsEntry(FriendlyIdEntityType.MILESTONE, "PH-001");
        // the unchanged project prefix is not history-checked
        verify(orgSettingsRepository, never()).findOtherTypeUsingPrefix(orgId, "PRJ", FriendlyIdEntityType.PROJECT);
        verify(orgSettingsRepository).findOtherTypeUsingPrefix(orgId, "TSK", FriendlyIdEntityType.JOB);
        verify(orgSettingsRepository).findOtherTypeUsingPrefix(orgId, "PH", FriendlyIdEntityType.MILESTONE);
        verify(orgSettingsRepository).findOtherTypeUsingPrefix(orgId, "TMP", FriendlyIdEntityType.TEMPLATE);
    }

    @Test
    @DisplayName("update rejects a prefix with digits")
    void update_shouldThrowBadRequest_forDigits() {
        givenOwnerWithDefaults();

        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("J1").build(),
                callerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Prefix must be 2–3 letters A–Z: \"J1\"");
        verify(orgSettingsRepository, never()).update(any());
    }

    @Test
    @DisplayName("update rejects a prefix that is too short or too long")
    void update_shouldThrowBadRequest_forWrongLength() {
        givenOwnerWithDefaults();

        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().projectPrefix("P").build(),
                callerId))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(orgId,
                UpdateOrgSettingsRequest.builder().templatePrefix("TASK").build(), callerId))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(orgId,
                UpdateOrgSettingsRequest.builder().milestonePrefix("   ").build(), callerId))
                .isInstanceOf(BadRequestException.class);
        verify(orgSettingsRepository, never()).update(any());
    }

    // --- update: distinctness ---

    @Test
    @DisplayName("update rejects two requested prefixes that are equal")
    void update_shouldThrowBadRequest_whenRequestedPrefixesCollide() {
        givenOwnerWithDefaults();

        assertThatThrownBy(() -> service.update(orgId,
                UpdateOrgSettingsRequest.builder().jobPrefix("TSK").milestonePrefix("tsk").build(), callerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Prefixes must be different from each other");
        verify(orgSettingsRepository, never()).update(any());
    }

    @Test
    @DisplayName("update rejects a requested prefix equal to another type's current prefix")
    void update_shouldThrowBadRequest_whenEqualToAnotherCurrentPrefix() {
        givenOwnerWithDefaults();

        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("MIL").build(),
                callerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Prefixes must be different from each other");
        verify(orgSettingsRepository, never()).findOtherTypeUsingPrefix(any(), any(), any());
    }

    @Test
    @DisplayName("update allows swapping two prefixes in one request when no records block it")
    void update_shouldAllowSwap_whenHistoryIsClear() {
        givenOwnerWithDefaults();
        givenUpdateEchoes();
        when(orgSettingsRepository.findOtherTypeUsingPrefix(eq(orgId), anyString(), any()))
                .thenReturn(Optional.empty());

        service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("MIL").milestonePrefix("JOB").build(),
                callerId);

        OrgSettingsModel saved = captureUpdate();
        assertThat(saved.getJobPrefix()).isEqualTo("MIL");
        assertThat(saved.getMilestonePrefix()).isEqualTo("JOB");
    }

    // --- update: history check ---

    @Test
    @DisplayName("update rejects a prefix that existing records of another type already carry")
    void update_shouldThrowBadRequest_whenPrefixUsedByAnotherType() {
        givenOwnerWithDefaults();
        when(orgSettingsRepository.findOtherTypeUsingPrefix(orgId, "OLD", FriendlyIdEntityType.MILESTONE))
                .thenReturn(Optional.of(FriendlyIdEntityType.JOB));

        assertThatThrownBy(() -> service.update(orgId,
                UpdateOrgSettingsRequest.builder().milestonePrefix("old").build(), callerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Prefix \"OLD\" is already used by existing jobs");
        verify(orgSettingsRepository, never()).update(any());
    }

    @Test
    @DisplayName("update names the type that blocks the prefix in the message, for every type")
    void update_shouldNameEachBlockingType() {
        givenOwnerWithDefaults();
        when(orgSettingsRepository.findOtherTypeUsingPrefix(eq(orgId), eq("ABC"), any()))
                .thenReturn(Optional.of(FriendlyIdEntityType.PROJECT))
                .thenReturn(Optional.of(FriendlyIdEntityType.MILESTONE))
                .thenReturn(Optional.of(FriendlyIdEntityType.TEMPLATE));

        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("ABC").build(),
                callerId))
                .hasMessage("Prefix \"ABC\" is already used by existing projects");
        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("ABC").build(),
                callerId))
                .hasMessage("Prefix \"ABC\" is already used by existing milestones");
        assertThatThrownBy(() -> service.update(orgId, UpdateOrgSettingsRequest.builder().jobPrefix("ABC").build(),
                callerId))
                .hasMessage("Prefix \"ABC\" is already used by existing templates");
    }

    @Test
    @DisplayName("update allows going back to a prefix the same type used before")
    void update_shouldAllowPrefix_usedBySameTypeBefore() {
        givenOrg();
        givenRole(OrganisationRole.OWNER);
        OrgSettingsModel current = defaults();
        current.setJobPrefix("TSK");
        when(orgSettingsRepository.findByOrgId(orgId)).thenReturn(Optional.of(current));
        // old JOB-nnn records are jobs, so the repository finds no *other* type using JOB
        when(orgSettingsRepository.findOtherTypeUsingPrefix(orgId, "JOB", FriendlyIdEntityType.JOB))
                .thenReturn(Optional.empty());
        givenUpdateEchoes();

        OrgSettingsModel result = service.update(orgId,
                UpdateOrgSettingsRequest.builder().jobPrefix("JOB").build(), callerId);

        assertThat(captureUpdate().getJobPrefix()).isEqualTo("JOB");
        assertThat(result.getNextIds()).containsEntry(FriendlyIdEntityType.JOB, "JOB-347");
    }

    // --- update: no-op ---

    @Test
    @DisplayName("update with the current values is a no-op: no history check, no write")
    void update_shouldBeNoOp_whenValuesUnchanged() {
        givenOwnerWithDefaults();

        OrgSettingsModel result = service.update(orgId,
                UpdateOrgSettingsRequest.builder().jobPrefix(" job ").projectPrefix("PRJ").build(), callerId);

        assertThat(result.getJobPrefix()).isEqualTo("JOB");
        assertThat(result.getNextIds()).containsEntry(FriendlyIdEntityType.JOB, "JOB-347");
        verify(orgSettingsRepository, never()).findOtherTypeUsingPrefix(any(), any(), any());
        verify(orgSettingsRepository, never()).update(any());
    }

    @Test
    @DisplayName("update with an empty body is a no-op")
    void update_shouldBeNoOp_whenBodyEmpty() {
        givenOwnerWithDefaults();

        OrgSettingsModel result = service.update(orgId, new UpdateOrgSettingsRequest(), callerId);

        assertThat(result.getMilestonePrefix()).isEqualTo("MIL");
        verify(orgSettingsRepository, never()).update(any());
    }

    @Test
    @DisplayName("update allows a prefix equal to the org slug")
    void update_shouldAllowPrefixEqualToSlug() {
        givenOwnerWithDefaults();
        when(orgSettingsRepository.findOtherTypeUsingPrefix(orgId, "ACM", FriendlyIdEntityType.PROJECT))
                .thenReturn(Optional.empty());
        givenUpdateEchoes();

        service.update(orgId, UpdateOrgSettingsRequest.builder().projectPrefix("ACM").build(), callerId);

        assertThat(captureUpdate().getProjectPrefix()).isEqualTo("ACM");
    }
}
