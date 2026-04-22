package com.opsclear.service;

import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("FriendlyIdResolver")
class FriendlyIdResolverTest {

    @Mock private FriendlyIdRepository friendlyIdRepository;
    @Mock private OrganisationRepository organisationRepository;

    private FriendlyIdResolver resolver;

    private UUID callerId;
    private UUID orgId;
    private UUID targetId;
    private OrganisationModel org;

    @BeforeEach
    void setUp() {
        resolver = new FriendlyIdResolver(friendlyIdRepository, organisationRepository);

        callerId = UUID.randomUUID();
        orgId    = UUID.randomUUID();
        targetId = UUID.randomUUID();

        org = OrganisationModel.builder().id(orgId).name("Acme").slug("ACM").createdBy(callerId).build();
    }

    // --- resolveProject ---

    @Test
    @DisplayName("resolveProject — UUID param is returned directly without DB lookup")
    void resolveProject_uuid_returnsDirectly() {
        UUID result = resolver.resolveProject(targetId.toString(), callerId);

        assertThat(result).isEqualTo(targetId);
        verify(organisationRepository, never()).findByMember(any());
        verify(friendlyIdRepository, never()).findProjectId(any(), any());
    }

    @Test
    @DisplayName("resolveProject — friendly ID resolves via org-scoped DB lookup")
    void resolveProject_friendlyId_resolvesViaDb() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(friendlyIdRepository.findProjectId("PRJ-001", orgId)).thenReturn(Optional.of(targetId));

        UUID result = resolver.resolveProject("PRJ-001", callerId);

        assertThat(result).isEqualTo(targetId);
        verify(friendlyIdRepository).findProjectId("PRJ-001", orgId);
    }

    @Test
    @DisplayName("resolveProject — friendly ID is case-insensitive (passed as-is to repo)")
    void resolveProject_friendlyIdLowercase_resolvesViaDb() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(friendlyIdRepository.findProjectId("prj-001", orgId)).thenReturn(Optional.of(targetId));

        UUID result = resolver.resolveProject("prj-001", callerId);

        assertThat(result).isEqualTo(targetId);
    }

    @Test
    @DisplayName("resolveProject — unknown friendly ID throws NotFoundException")
    void resolveProject_unknownFriendlyId_throwsNotFound() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(friendlyIdRepository.findProjectId(eq("PRJ-999"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveProject("PRJ-999", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("resolveProject — caller with no org throws ForbiddenException")
    void resolveProject_noOrg_throwsForbidden() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveProject("PRJ-001", callerId))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- resolveJob ---

    @Test
    @DisplayName("resolveJob — UUID param is returned directly without DB lookup")
    void resolveJob_uuid_returnsDirectly() {
        UUID result = resolver.resolveJob(targetId.toString(), callerId);

        assertThat(result).isEqualTo(targetId);
        verify(friendlyIdRepository, never()).findJobId(any(), any());
    }

    @Test
    @DisplayName("resolveJob — friendly ID resolves via org-scoped DB lookup")
    void resolveJob_friendlyId_resolvesViaDb() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(friendlyIdRepository.findJobId("JOB-042", orgId)).thenReturn(Optional.of(targetId));

        UUID result = resolver.resolveJob("JOB-042", callerId);

        assertThat(result).isEqualTo(targetId);
    }

    @Test
    @DisplayName("resolveJob — unknown friendly ID throws NotFoundException")
    void resolveJob_unknownFriendlyId_throwsNotFound() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(friendlyIdRepository.findJobId(eq("JOB-999"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveJob("JOB-999", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("resolveJob — caller with no org throws ForbiddenException")
    void resolveJob_noOrg_throwsForbidden() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveJob("JOB-001", callerId))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- resolveMilestone ---

    @Test
    @DisplayName("resolveMilestone — UUID param is returned directly without DB lookup")
    void resolveMilestone_uuid_returnsDirectly() {
        UUID result = resolver.resolveMilestone(targetId.toString(), callerId);

        assertThat(result).isEqualTo(targetId);
        verify(friendlyIdRepository, never()).findMilestoneId(any(), any());
    }

    @Test
    @DisplayName("resolveMilestone — friendly ID resolves via org-scoped DB lookup")
    void resolveMilestone_friendlyId_resolvesViaDb() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(friendlyIdRepository.findMilestoneId("MIL-007", orgId)).thenReturn(Optional.of(targetId));

        UUID result = resolver.resolveMilestone("MIL-007", callerId);

        assertThat(result).isEqualTo(targetId);
    }

    @Test
    @DisplayName("resolveMilestone — unknown friendly ID throws NotFoundException")
    void resolveMilestone_unknownFriendlyId_throwsNotFound() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.of(org));
        when(friendlyIdRepository.findMilestoneId(eq("MIL-999"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveMilestone("MIL-999", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Milestone not found");
    }

    @Test
    @DisplayName("resolveMilestone — caller with no org throws ForbiddenException")
    void resolveMilestone_noOrg_throwsForbidden() {
        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveMilestone("MIL-001", callerId))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- isFriendlyId ---

    @Test
    @DisplayName("isFriendlyId — matches valid friendly ID patterns")
    void isFriendlyId_validPatterns() {
        assertThat(FriendlyIdResolver.isFriendlyId("PRJ-001")).isTrue();
        assertThat(FriendlyIdResolver.isFriendlyId("JOB-042")).isTrue();
        assertThat(FriendlyIdResolver.isFriendlyId("MIL-007")).isTrue();
        assertThat(FriendlyIdResolver.isFriendlyId("prj-001")).isTrue();
        assertThat(FriendlyIdResolver.isFriendlyId("JOB-1000")).isTrue();
    }

    @Test
    @DisplayName("isFriendlyId — does not match UUIDs or invalid strings")
    void isFriendlyId_invalidPatterns() {
        assertThat(FriendlyIdResolver.isFriendlyId(UUID.randomUUID().toString())).isFalse();
        assertThat(FriendlyIdResolver.isFriendlyId("random-string")).isFalse();
        assertThat(FriendlyIdResolver.isFriendlyId("123-456")).isFalse();
        assertThat(FriendlyIdResolver.isFriendlyId("P-1")).isFalse();
    }
}
