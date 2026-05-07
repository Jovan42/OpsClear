package com.opsclear.service;

import com.opsclear.dto.OrgSubscriptionResponse;
import com.opsclear.dto.UpsertSubscriptionRequest;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService")
class SubscriptionServiceTest {

    @Mock private OrgSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionTierRepository tierRepository;
    @Mock private SubscriptionAddonRepository addonRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private ProjectRepository projectRepository;

    private SubscriptionService service;

    private UUID orgId;
    private UUID userId;
    private UUID tierId;
    private SubscriptionTierModel tier;
    private OrgSubscriptionModel subscription;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(
                subscriptionRepository, tierRepository, addonRepository,
                organisationRepository, projectRepository);

        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        tierId = UUID.randomUUID();

        tier = SubscriptionTierModel.builder()
                .id(tierId)
                .maxMembers(5)
                .maxProjects(3)
                .priceMonthly(2900)
                .priceAnnual(2417)
                .currency("RSD")
                .displayOrder(1)
                .build();

        // addonIds is empty — addonRepository.findByIds won't be called for this subscription
        subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .tierId(tierId)
                .billingCycle("MONTHLY")
                .isInternal(false)
                .updatedAt(Instant.now())
                .addonIds(List.of())
                .build();
    }

    // ─── getSubscription ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getSubscription_shouldReturnSubscription_whenRequesterIsMember")
    void getSubscription_shouldReturnSubscription_whenRequesterIsMember() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.MEMBER));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        OrgSubscriptionResponse response = service.getSubscription(orgId, userId);

        assertThat(response.getId()).isEqualTo(subscription.getId());
        assertThat(response.getBillingCycle()).isEqualTo("MONTHLY");
        assertThat(response.getTier().getId()).isEqualTo(tierId);
    }

    @Test
    @DisplayName("getSubscription_shouldThrowNotFoundException_whenRequesterIsNotMember")
    void getSubscription_shouldThrowNotFoundException_whenRequesterIsNotMember() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubscription(orgId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Organisation not found");

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("getSubscription_shouldThrowNotFoundException_whenNoSubscriptionExists")
    void getSubscription_shouldThrowNotFoundException_whenNoSubscriptionExists() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubscription(orgId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No subscription found");
    }

    @Test
    @DisplayName("getSubscription_shouldThrowNotFoundException_whenTierNotFound")
    void getSubscription_shouldThrowNotFoundException_whenTierNotFound() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubscription(orgId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Subscription tier not found");
    }

    // ─── upsertSubscription — access control ──────────────────────────────────

    @Test
    @DisplayName("upsertSubscription_shouldThrowNotFoundException_whenRequesterNotInOrg")
    void upsertSubscription_shouldThrowNotFoundException_whenRequesterNotInOrg() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.empty());
        var req = request("MONTHLY");

        assertThatThrownBy(() -> service.upsertSubscription(orgId, userId, req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Organisation not found");

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("upsertSubscription_shouldThrowForbiddenException_whenRequesterIsNotOwner")
    void upsertSubscription_shouldThrowForbiddenException_whenRequesterIsNotOwner() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.MEMBER));
        var req = request("MONTHLY");

        assertThatThrownBy(() -> service.upsertSubscription(orgId, userId, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the organisation owner");

        verifyNoInteractions(subscriptionRepository);
    }

    // ─── upsertSubscription — create vs update ────────────────────────────────

    @Test
    @DisplayName("upsertSubscription_shouldCreateSubscription_whenNoneExists")
    void upsertSubscription_shouldCreateSubscription_whenNoneExists() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.countMembers(orgId)).thenReturn(3);
        when(projectRepository.countActiveByOrgId(orgId)).thenReturn(2);
        when(subscriptionRepository.create(eq(orgId), eq(tierId), eq("MONTHLY"), any())).thenReturn(subscription);
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        OrgSubscriptionResponse response = service.upsertSubscription(orgId, userId, request("MONTHLY"));

        assertThat(response.getId()).isEqualTo(subscription.getId());
        verify(subscriptionRepository).create(eq(orgId), eq(tierId), eq("MONTHLY"), any());
    }

    @Test
    @DisplayName("upsertSubscription_shouldUpdateSubscription_whenOneAlreadyExists")
    void upsertSubscription_shouldUpdateSubscription_whenOneAlreadyExists() {
        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(organisationRepository.countMembers(orgId)).thenReturn(3);
        when(projectRepository.countActiveByOrgId(orgId)).thenReturn(2);
        when(subscriptionRepository.update(eq(subscription.getId()), eq(orgId), eq(tierId), eq("ANNUAL"), any())).thenReturn(subscription);

        service.upsertSubscription(orgId, userId, request("ANNUAL"));

        verify(subscriptionRepository).update(eq(subscription.getId()), eq(orgId), eq(tierId), eq("ANNUAL"), any());
    }

    // ─── upsertSubscription — downgrade validation ───────────────────────────

    @Test
    @DisplayName("upsertSubscription_shouldRejectDowngrade_whenTooManyMembers")
    void upsertSubscription_shouldRejectDowngrade_whenTooManyMembers() {
        SubscriptionTierModel smallTier = SubscriptionTierModel.builder()
                .id(tierId).maxMembers(2).maxProjects(10)
                .priceMonthly(1000).priceAnnual(833).currency("RSD").displayOrder(1).build();

        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(smallTier));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.countMembers(orgId)).thenReturn(5);
        var req = request("MONTHLY");

        assertThatThrownBy(() -> service.upsertSubscription(orgId, userId, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("5 members")
                .hasMessageContaining("allows 2");
    }

    @Test
    @DisplayName("upsertSubscription_shouldRejectDowngrade_whenTooManyActiveProjects")
    void upsertSubscription_shouldRejectDowngrade_whenTooManyActiveProjects() {
        SubscriptionTierModel smallTier = SubscriptionTierModel.builder()
                .id(tierId).maxMembers(10).maxProjects(1)
                .priceMonthly(1000).priceAnnual(833).currency("RSD").displayOrder(1).build();

        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(smallTier));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.countMembers(orgId)).thenReturn(3);
        when(projectRepository.countActiveByOrgId(orgId)).thenReturn(4);
        var req = request("MONTHLY");

        assertThatThrownBy(() -> service.upsertSubscription(orgId, userId, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("4 active projects")
                .hasMessageContaining("allows 1");
    }

    @Test
    @DisplayName("upsertSubscription_shouldSkipProjectCountCheck_whenTierHasUnlimitedProjects")
    void upsertSubscription_shouldSkipProjectCountCheck_whenTierHasUnlimitedProjects() {
        SubscriptionTierModel unlimitedTier = SubscriptionTierModel.builder()
                .id(tierId).maxMembers(50).maxProjects(null)
                .priceMonthly(27900).priceAnnual(23250).currency("RSD").displayOrder(35).build();

        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(unlimitedTier));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.countMembers(orgId)).thenReturn(30);
        when(subscriptionRepository.create(any(), any(), any(), any())).thenReturn(subscription);

        OrgSubscriptionResponse response = service.upsertSubscription(orgId, userId, request("MONTHLY"));

        assertThat(response).isNotNull();
        verify(projectRepository, never()).countActiveByOrgId(any());
    }

    @Test
    @DisplayName("upsertSubscription_shouldBypassDowngradeValidation_whenSubscriptionIsInternal")
    void upsertSubscription_shouldBypassDowngradeValidation_whenSubscriptionIsInternal() {
        OrgSubscriptionModel internalSub = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).tierId(tierId)
                .billingCycle("MONTHLY").isInternal(true)
                .updatedAt(Instant.now()).addonIds(List.of())
                .build();

        SubscriptionTierModel tinyTier = SubscriptionTierModel.builder()
                .id(tierId).maxMembers(1).maxProjects(1)
                .priceMonthly(500).priceAnnual(417).currency("RSD").displayOrder(1).build();

        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tinyTier));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(internalSub));
        when(subscriptionRepository.update(any(), any(), any(), any(), any())).thenReturn(internalSub);

        service.upsertSubscription(orgId, userId, request("MONTHLY"));

        verify(organisationRepository, never()).countMembers(any());
        verify(projectRepository, never()).countActiveByOrgId(any());
    }

    // ─── upsertSubscription — add-ons ─────────────────────────────────────────

    @Test
    @DisplayName("upsertSubscription_shouldPassAddonIdsToRepository")
    void upsertSubscription_shouldPassAddonIdsToRepository() {
        UUID addonId = UUID.randomUUID();
        UpsertSubscriptionRequest req = new UpsertSubscriptionRequest(tierId, "MONTHLY", List.of(addonId));

        OrgSubscriptionModel subWithAddon = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).tierId(tierId)
                .billingCycle("MONTHLY").isInternal(false)
                .updatedAt(Instant.now()).addonIds(List.of(addonId))
                .build();

        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.countMembers(orgId)).thenReturn(3);
        when(projectRepository.countActiveByOrgId(orgId)).thenReturn(2);
        when(subscriptionRepository.create(orgId, tierId, "MONTHLY", new HashSet<>(List.of(addonId))))
                .thenReturn(subWithAddon);
        when(addonRepository.findByIds(any())).thenReturn(List.of());

        service.upsertSubscription(orgId, userId, req);

        verify(subscriptionRepository).create(orgId, tierId, "MONTHLY", new HashSet<>(List.of(addonId)));
    }

    // ─── buildResponse — pricing ──────────────────────────────────────────────

    @Test
    @DisplayName("getSubscription_shouldCalculateMonthlyTotal_forMonthlyBillingCycle")
    void getSubscription_shouldCalculateMonthlyTotal_forMonthlyBillingCycle() {
        SubscriptionAddonModel addon = SubscriptionAddonModel.builder()
                .id(UUID.randomUUID()).key("DASHBOARD").name("Dashboard")
                .priceMonthly(990).priceAnnual(825).available(true).displayOrder(1).build();

        OrgSubscriptionModel subWithAddon = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).tierId(tierId)
                .billingCycle("MONTHLY").isInternal(false)
                .updatedAt(Instant.now()).addonIds(List.of(addon.getId()))
                .build();

        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subWithAddon));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(addonRepository.findByIds(any())).thenReturn(List.of(addon));

        OrgSubscriptionResponse response = service.getSubscription(orgId, userId);

        assertThat(response.getTotalMonthly()).isEqualTo(2900 + 990);
    }

    @Test
    @DisplayName("getSubscription_shouldCalculateAnnualTotal_forAnnualBillingCycle")
    void getSubscription_shouldCalculateAnnualTotal_forAnnualBillingCycle() {
        SubscriptionAddonModel addon = SubscriptionAddonModel.builder()
                .id(UUID.randomUUID()).key("DASHBOARD").name("Dashboard")
                .priceMonthly(990).priceAnnual(825).available(true).displayOrder(1).build();

        OrgSubscriptionModel annualSub = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).tierId(tierId)
                .billingCycle("ANNUAL").isInternal(false)
                .updatedAt(Instant.now()).addonIds(List.of(addon.getId()))
                .build();

        when(organisationRepository.findMemberRole(orgId, userId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(subscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(annualSub));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        when(addonRepository.findByIds(any())).thenReturn(List.of(addon));

        OrgSubscriptionResponse response = service.getSubscription(orgId, userId);

        assertThat(response.getTotalMonthly()).isEqualTo(2417 + 825);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private UpsertSubscriptionRequest request(String cycle) {
        return new UpsertSubscriptionRequest(tierId, cycle, null);
    }
}
