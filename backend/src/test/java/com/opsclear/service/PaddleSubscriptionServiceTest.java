package com.opsclear.service;

import com.opsclear.dto.PreviewSubscriptionUpdateResponse;
import com.opsclear.dto.UpdatePaddleSubscriptionRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.PaddleCatalogSyncResult;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleCustomer;
import com.opsclear.paddle.PaddlePreviewImmediateTransaction;
import com.opsclear.paddle.PaddlePreviewTotals;
import com.opsclear.paddle.PaddlePreviewTransactionDetails;
import com.opsclear.paddle.PaddlePrice;
import com.opsclear.paddle.PaddlePriceResolver;
import com.opsclear.paddle.PaddleProduct;
import com.opsclear.paddle.PaddleSubscription;
import com.opsclear.paddle.PaddleSubscriptionBillingPeriod;
import com.opsclear.paddle.PaddleSubscriptionItem;
import com.opsclear.paddle.PaddleSubscriptionPreview;
import com.opsclear.paddle.PaddleTransaction;
import com.opsclear.paddle.PaddleTransactionDetails;
import com.opsclear.paddle.PaddleTransactionTotals;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaddleSubscriptionService")
class PaddleSubscriptionServiceTest {

    @Mock private OrgSubscriptionRepository orgSubscriptionRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionTierRepository tierRepository;
    @Mock private SubscriptionAddonRepository addonRepository;
    @Mock private PaddleClient paddleClient;
    @Mock private PaddlePriceResolver priceResolver;

    private PaddleSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PaddleSubscriptionService(
                orgSubscriptionRepository, organisationRepository, userRepository, tierRepository, addonRepository,
                paddleClient, priceResolver);
    }

    // --- initiate ---

    @Test
    @DisplayName("initiate creates a Paddle customer and persists its id on organisations, even with no "
            + "org_subscriptions row yet")
    void initiate_shouldCreatePaddleCustomer_andPersistId() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.findPaddleCustomerId(orgId)).thenReturn(Optional.empty());
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.of(UserModel.builder().id(ownerId).email("owner@example.com").build()));
        when(paddleClient.createCustomer("owner@example.com", "Acme Corp"))
                .thenReturn(new PaddleCustomer("ctm_123", "owner@example.com"));

        String result = service.initiate(orgId, ownerId);

        assertThat(result).isEqualTo("ctm_123");
        verify(organisationRepository).updatePaddleCustomerId(orgId, "ctm_123");
    }

    @Test
    @DisplayName("initiate is idempotent when the org already has a Paddle customer")
    void initiate_shouldSkipCreation_whenCustomerAlreadyExists() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.findPaddleCustomerId(orgId)).thenReturn(Optional.of("ctm_existing"));

        String result = service.initiate(orgId, ownerId);

        assertThat(result).isEqualTo("ctm_existing");
        verify(paddleClient, never()).createCustomer(anyString(), anyString());
        verify(organisationRepository, never()).updatePaddleCustomerId(any(), anyString());
    }

    @Test
    @DisplayName("initiate throws BadRequestException for an internal org")
    void initiate_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.initiate(orgId, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Internal organisations are not billed and have no Paddle subscription");
        verify(paddleClient, never()).createCustomer(anyString(), anyString());
    }

    @Test
    @DisplayName("initiate throws NotFoundException when the organisation record is missing")
    void initiate_shouldThrow_whenOrgRecordMissing() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(orgId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organisation not found");
        verify(paddleClient, never()).createCustomer(anyString(), anyString());
    }

    @Test
    @DisplayName("initiate throws NotFoundException when the requesting user record is missing")
    void initiate_shouldThrow_whenUserRecordMissing() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.findPaddleCustomerId(orgId)).thenReturn(Optional.empty());
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(orgId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
        verify(paddleClient, never()).createCustomer(anyString(), anyString());
    }

    @Test
    @DisplayName("initiate throws ForbiddenException for a non-owner")
    void initiate_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.initiate(orgId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER role required");
    }

    @Test
    @DisplayName("initiate throws NotFoundException when the caller is not a member")
    void initiate_shouldThrow_whenCallerNotAMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(orgId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organisation not found");
    }

    // --- updateSubscriptionItems ---

    @Test
    @DisplayName("updateSubscriptionItems resolves price ids, calls Paddle, and updates the local record on an upgrade")
    void updateSubscriptionItems_shouldSyncPaddle_andUpdateLocalRecord_onUpgrade() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID addonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of(addonId)).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(10).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(20).build()));
        when(addonRepository.findByIds(Set.of(addonId))).thenReturn(
                List.of(SubscriptionAddonModel.builder().id(addonId).priceMonthly(5).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(priceResolver.resolveAddonPriceId(addonId, "MONTHLY")).thenReturn("pri_addon");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", null));

        PaddleSubscription result = service.updateSubscriptionItems(orgId, ownerId, request);

        assertThat(result.status()).isEqualTo("active");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaddleSubscriptionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), itemsCaptor.capture(), eq("prorated_immediately"));
        assertThat(itemsCaptor.getValue()).containsExactlyInAnyOrder(
                new PaddleSubscriptionItem("pri_tier", 1), new PaddleSubscriptionItem("pri_addon", 1));

        verify(orgSubscriptionRepository).update(subscriptionId, orgId, tierId, "MONTHLY", Set.of(addonId));
        verify(orgSubscriptionRepository, never()).schedulePendingDowngrade(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateSubscriptionItems sends only the tier item when addonIds is null")
    void updateSubscriptionItems_shouldSendTierOnly_whenAddonIdsIsNull() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(10).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(20).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", null));

        service.updateSubscriptionItems(orgId, ownerId, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaddleSubscriptionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), itemsCaptor.capture(), eq("prorated_immediately"));
        assertThat(itemsCaptor.getValue()).containsExactly(new PaddleSubscriptionItem("pri_tier", 1));

        verify(orgSubscriptionRepository).update(subscriptionId, orgId, tierId, "MONTHLY", Set.of());
    }

    @Test
    @DisplayName("updateSubscriptionItems defers to Paddle's next billing period and schedules a pending "
            + "downgrade instead of touching the active tier, on a net price decrease")
    void updateSubscriptionItems_shouldSchedulePendingDowngrade_onDowngrade() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(30).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(10).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        Instant nextBilledAt = Instant.parse("2026-09-01T00:00:00Z");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", nextBilledAt));

        service.updateSubscriptionItems(orgId, ownerId, request);

        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period"));
        verify(orgSubscriptionRepository)
                .schedulePendingDowngrade(subscriptionId, orgId, tierId, Set.of(), nextBilledAt);
        verify(orgSubscriptionRepository, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateSubscriptionItems overwrites an already-pending downgrade with a new one, rather than "
            + "blocking it — Paddle's full_next_billing_period calls don't conflict with each other")
    void updateSubscriptionItems_shouldOverwritePendingDowngrade_withANewOne() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").pendingTierId(UUID.randomUUID()).build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(30).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(10).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        Instant nextBilledAt = Instant.parse("2026-09-01T00:00:00Z");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", nextBilledAt));

        service.updateSubscriptionItems(orgId, ownerId, request);

        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period"));
        verify(orgSubscriptionRepository)
                .schedulePendingDowngrade(subscriptionId, orgId, tierId, Set.of(), nextBilledAt);
    }

    @Test
    @DisplayName("updateSubscriptionItems throws ConflictException for a downgrade when a cancellation is "
            + "already scheduled — Paddle rejects a second scheduled change")
    void updateSubscriptionItems_shouldThrow_whenCancellationAlreadyScheduled() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY")
                .paddleScheduledCancellationAt(Instant.parse("2026-09-01T00:00:00Z")).build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(30).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(10).build()));

        assertThatThrownBy(() -> service.updateSubscriptionItems(orgId, ownerId, request))
                .isInstanceOf(ConflictException.class);
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("updateSubscriptionItems does not guard an upgrade against an already-scheduled change — "
            + "Paddle allows prorated_immediately regardless")
    void updateSubscriptionItems_shouldAllowUpgrade_evenWhenChangeAlreadyScheduled() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY")
                .paddleScheduledCancellationAt(Instant.parse("2026-09-01T00:00:00Z")).build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(10).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", null));

        service.updateSubscriptionItems(orgId, ownerId, request);

        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately"));
    }

    @Test
    @DisplayName("updateSubscriptionItems throws ConflictException when the request both adds a pricier addon "
            + "and removes a cheaper one in the same change")
    void updateSubscriptionItems_shouldThrow_whenChangeIsMixed() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID existingAddonId = UUID.randomUUID();
        UUID newAddonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(tierId)
                .addonIds(List.of(existingAddonId))
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of(newAddonId)).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));

        assertThatThrownBy(() -> service.updateSubscriptionItems(orgId, ownerId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Paddle.MIXED_UPGRADE_DOWNGRADE_NOT_ALLOWED);
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("updateSubscriptionItems allows removing an addon on its own, with nothing added, as a "
            + "pure (non-mixed) downgrade")
    void updateSubscriptionItems_shouldAllow_whenOnlyRemovingAnAddon() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID existingAddonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(tierId)
                .addonIds(List.of(existingAddonId))
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));
        when(addonRepository.findByIds(Set.of(existingAddonId)))
                .thenReturn(List.of(SubscriptionAddonModel.builder().id(existingAddonId).priceMonthly(9).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", Instant.parse("2026-09-01T00:00:00Z")));

        service.updateSubscriptionItems(orgId, ownerId, request);

        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period"));
    }

    @Test
    @DisplayName("updateSubscriptionItems allows adding an addon on its own, at the same tier, with nothing "
            + "removed, as a pure (non-mixed) upgrade")
    void updateSubscriptionItems_shouldAllow_whenOnlyAddingAnAddon() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID newAddonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(tierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of(newAddonId)).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));
        when(addonRepository.findByIds(Set.of(newAddonId)))
                .thenReturn(List.of(SubscriptionAddonModel.builder().id(newAddonId).priceMonthly(9).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(priceResolver.resolveAddonPriceId(newAddonId, "MONTHLY")).thenReturn("pri_addon");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", null));

        service.updateSubscriptionItems(orgId, ownerId, request);

        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately"));
    }

    @Test
    @DisplayName("updateSubscriptionItems evaluates the add/remove diff against a non-empty current addon set "
            + "even when the resubmitted selection is unchanged, rather than only ever short-circuiting on an "
            + "empty collection")
    void updateSubscriptionItems_shouldEvaluateAddonDiff_whenResubmittingTheSameSelection() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID addonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(tierId)
                .addonIds(List.of(addonId))
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of(addonId)).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));
        when(addonRepository.findByIds(Set.of(addonId)))
                .thenReturn(List.of(SubscriptionAddonModel.builder().id(addonId).priceMonthly(9).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(priceResolver.resolveAddonPriceId(addonId, "MONTHLY")).thenReturn("pri_addon");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", Instant.parse("2026-09-01T00:00:00Z")));

        service.updateSubscriptionItems(orgId, ownerId, request);

        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period"));
    }

    @Test
    @DisplayName("updateSubscriptionItems throws ConflictException when there's no Paddle subscription yet")
    void updateSubscriptionItems_shouldThrow_whenNoPaddleSubscriptionYet() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId(null).build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder().tierId(tierId).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.updateSubscriptionItems(orgId, ownerId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This organisation has not completed Paddle checkout yet — there is no "
                        + "subscription to update");
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("updateSubscriptionItems throws BadRequestException for an internal org")
    void updateSubscriptionItems_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder().tierId(tierId).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.updateSubscriptionItems(orgId, ownerId, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("updateSubscriptionItems throws NotFoundException when the tier does not exist")
    void updateSubscriptionItems_shouldThrow_whenTierNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder().tierId(tierId).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSubscriptionItems(orgId, ownerId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subscription tier not found");
    }

    @Test
    @DisplayName("updateSubscriptionItems throws ForbiddenException for a non-owner")
    void updateSubscriptionItems_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UpdatePaddleSubscriptionRequest request =
                UpdatePaddleSubscriptionRequest.builder().tierId(UUID.randomUUID()).build();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.updateSubscriptionItems(orgId, memberId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- previewUpdateSubscriptionItems ---

    @Test
    @DisplayName("previewUpdateSubscriptionItems returns the prorated charge for an upgrade, with no billing "
            + "applied by Paddle")
    void previewUpdateSubscriptionItems_shouldReturnImmediateCharge_onUpgrade() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(10).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(paddleClient.previewUpdateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscriptionPreview(
                        null,
                        new PaddlePreviewImmediateTransaction(
                                new PaddlePreviewTransactionDetails(new PaddlePreviewTotals("1250", "EUR")))));

        PreviewSubscriptionUpdateResponse result = service.previewUpdateSubscriptionItems(orgId, ownerId, request);

        assertThat(result.isUpgrade()).isTrue();
        assertThat(result.getImmediateChargeAmount()).isEqualTo(12);
        assertThat(result.getCurrency()).isEqualTo("EUR");
        assertThat(result.getEffectiveAt()).isNull();
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems returns no charge and the period end date for a downgrade")
    void previewUpdateSubscriptionItems_shouldReturnEffectiveDate_onDowngrade() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        Instant periodEnd = Instant.parse("2026-09-01T00:00:00Z");

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(30).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(10).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(paddleClient.previewUpdateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period")))
                .thenReturn(new PaddleSubscriptionPreview(
                        new PaddleSubscriptionBillingPeriod(Instant.parse("2026-08-01T00:00:00Z"), periodEnd),
                        null));

        PreviewSubscriptionUpdateResponse result = service.previewUpdateSubscriptionItems(orgId, ownerId, request);

        assertThat(result.isUpgrade()).isFalse();
        assertThat(result.getImmediateChargeAmount()).isNull();
        assertThat(result.getCurrency()).isNull();
        assertThat(result.getEffectiveAt()).isEqualTo(periodEnd);
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems allows previewing a new downgrade even when one is already "
            + "pending, since it would simply overwrite it")
    void previewUpdateSubscriptionItems_shouldAllowPreview_whenADowngradeIsAlreadyPending() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        Instant periodEnd = Instant.parse("2026-09-01T00:00:00Z");

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").pendingTierId(UUID.randomUUID()).build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of()).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(30).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(10).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(paddleClient.previewUpdateSubscriptionItems(eq("sub_123"), any(), eq("full_next_billing_period")))
                .thenReturn(new PaddleSubscriptionPreview(
                        new PaddleSubscriptionBillingPeriod(Instant.parse("2026-08-01T00:00:00Z"), periodEnd),
                        null));

        PreviewSubscriptionUpdateResponse result = service.previewUpdateSubscriptionItems(orgId, ownerId, request);

        assertThat(result.isUpgrade()).isFalse();
        assertThat(result.getEffectiveAt()).isEqualTo(periodEnd);
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems throws ConflictException when the request both adds a pricier "
            + "addon and removes a cheaper one in the same change")
    void previewUpdateSubscriptionItems_shouldThrow_whenChangeIsMixed() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID existingAddonId = UUID.randomUUID();
        UUID newAddonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(tierId)
                .addonIds(List.of(existingAddonId))
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of(newAddonId)).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));

        assertThatThrownBy(() -> service.previewUpdateSubscriptionItems(orgId, ownerId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Paddle.MIXED_UPGRADE_DOWNGRADE_NOT_ALLOWED);
        verify(paddleClient, never()).previewUpdateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems throws ConflictException when there's no Paddle subscription yet")
    void previewUpdateSubscriptionItems_shouldThrow_whenNoPaddleSubscriptionYet() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId(null).build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder().tierId(tierId).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.previewUpdateSubscriptionItems(orgId, ownerId, request))
                .isInstanceOf(ConflictException.class);
        verify(paddleClient, never()).previewUpdateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems throws ForbiddenException for a non-owner")
    void previewUpdateSubscriptionItems_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UpdatePaddleSubscriptionRequest request =
                UpdatePaddleSubscriptionRequest.builder().tierId(UUID.randomUUID()).build();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.previewUpdateSubscriptionItems(orgId, memberId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems treats a null addonIds request as no add-ons")
    void previewUpdateSubscriptionItems_shouldTreatNullAddonIds_asNoAddons() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(currentTierId).priceMonthly(10).build()));
        when(tierRepository.findById(tierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).priceMonthly(30).build()));
        when(priceResolver.resolveTierPriceId(tierId, "MONTHLY")).thenReturn("pri_tier");
        when(paddleClient.previewUpdateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscriptionPreview(
                        null,
                        new PaddlePreviewImmediateTransaction(
                                new PaddlePreviewTransactionDetails(new PaddlePreviewTotals("2000", "EUR")))));

        PreviewSubscriptionUpdateResponse result = service.previewUpdateSubscriptionItems(orgId, ownerId, request);

        assertThat(result.isUpgrade()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaddleSubscriptionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paddleClient).previewUpdateSubscriptionItems(eq("sub_123"), itemsCaptor.capture(), eq("prorated_immediately"));
        assertThat(itemsCaptor.getValue()).containsExactly(new PaddleSubscriptionItem("pri_tier", 1));
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems compares annual prices (tier and add-ons) for an ANNUAL subscription")
    void previewUpdateSubscriptionItems_shouldCompareAnnualPrices_forAnnualBillingCycle() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID currentTierId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID addonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).tierId(currentTierId).addonIds(List.of())
                .paddleSubscriptionId("sub_123").billingCycle("ANNUAL").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of(addonId)).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(currentTierId)).thenReturn(Optional.of(
                SubscriptionTierModel.builder().id(currentTierId).priceMonthly(100).priceAnnual(10).build()));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(
                SubscriptionTierModel.builder().id(tierId).priceMonthly(5).priceAnnual(20).build()));
        when(addonRepository.findByIds(Set.of(addonId))).thenReturn(
                List.of(SubscriptionAddonModel.builder().id(addonId).priceMonthly(1).priceAnnual(5).build()));
        when(priceResolver.resolveTierPriceId(tierId, "ANNUAL")).thenReturn("pri_tier");
        when(priceResolver.resolveAddonPriceId(addonId, "ANNUAL")).thenReturn("pri_addon");
        when(paddleClient.previewUpdateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscriptionPreview(
                        null,
                        new PaddlePreviewImmediateTransaction(
                                new PaddlePreviewTransactionDetails(new PaddlePreviewTotals("1500", "EUR")))));

        PreviewSubscriptionUpdateResponse result = service.previewUpdateSubscriptionItems(orgId, ownerId, request);

        // old total (annual) = 10, new total (annual) = 20 + 5 = 25 — an upgrade despite
        // the monthly prices alone (100 -> 5) implying the opposite, proving the annual
        // ternary branch (not the monthly one) is what actually drove the comparison.
        assertThat(result.isUpgrade()).isTrue();
    }

    // --- cancel ---

    @Test
    @DisplayName("cancel schedules an end-of-period cancellation via Paddle")
    void cancel_shouldScheduleEndOfPeriodCancellation() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123").build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(paddleClient.cancelSubscription("sub_123"))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", null));

        PaddleSubscription result = service.cancel(orgId, ownerId);

        assertThat(result.status()).isEqualTo("active");
        verify(paddleClient).cancelSubscription("sub_123");
    }

    @Test
    @DisplayName("cancel throws ConflictException when there's no Paddle subscription yet")
    void cancel_shouldThrow_whenNoPaddleSubscriptionYet() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.cancel(orgId, ownerId))
                .isInstanceOf(ConflictException.class);
        verify(paddleClient, never()).cancelSubscription(anyString());
    }

    @Test
    @DisplayName("cancel throws ConflictException when a cancellation is already scheduled")
    void cancel_shouldThrow_whenCancellationAlreadyScheduled() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123")
                .paddleScheduledCancellationAt(Instant.parse("2024-10-12T07:20:50.52Z")).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.cancel(orgId, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Paddle.CANCELLATION_ALREADY_SCHEDULED);
        verify(paddleClient, never()).cancelSubscription(anyString());
    }

    @Test
    @DisplayName("cancel throws BadRequestException for an internal org")
    void cancel_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.cancel(orgId, ownerId))
                .isInstanceOf(BadRequestException.class);
        verify(paddleClient, never()).cancelSubscription(anyString());
    }

    @Test
    @DisplayName("cancel throws NotFoundException when the org has no subscription record yet")
    void cancel_shouldThrow_whenNoSubscriptionRecord() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(orgId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("cancel throws ForbiddenException for a non-owner")
    void cancel_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.cancel(orgId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("cancel throws NotFoundException when the caller is not a member")
    void cancel_shouldThrow_whenCallerNotAMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(orgId, callerId))
                .isInstanceOf(NotFoundException.class);
    }

    // --- resume ---

    @Test
    @DisplayName("resume removes the scheduled cancellation via Paddle and clears it locally")
    void resume_shouldRemoveScheduledCancellation() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123")
                .paddleScheduledCancellationAt(Instant.parse("2024-10-12T07:20:50.52Z")).build();
        OrgSubscriptionModel resumed = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123")
                .subscriptionStatus("ACTIVE").paddleScheduledCancellationAt(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(paddleClient.removeScheduledCancellation("sub_123"))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123", null));
        when(orgSubscriptionRepository.clearScheduledCancellation(subscriptionId, orgId)).thenReturn(resumed);

        OrgSubscriptionModel result = service.resume(orgId, ownerId);

        assertThat(result.getPaddleScheduledCancellationAt()).isNull();
        verify(paddleClient).removeScheduledCancellation("sub_123");
        verify(orgSubscriptionRepository).clearScheduledCancellation(subscriptionId, orgId);
    }

    @Test
    @DisplayName("resume throws ConflictException when nothing is scheduled to resume")
    void resume_shouldThrow_whenNoCancellationScheduled() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123")
                .paddleScheduledCancellationAt(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.resume(orgId, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Paddle.NO_CANCELLATION_SCHEDULED);
        verify(paddleClient, never()).removeScheduledCancellation(anyString());
    }

    @Test
    @DisplayName("resume throws ConflictException when there's no Paddle subscription yet")
    void resume_shouldThrow_whenNoPaddleSubscriptionYet() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.resume(orgId, ownerId))
                .isInstanceOf(ConflictException.class);
        verify(paddleClient, never()).removeScheduledCancellation(anyString());
    }

    @Test
    @DisplayName("resume throws BadRequestException for an internal org")
    void resume_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.resume(orgId, ownerId))
                .isInstanceOf(BadRequestException.class);
        verify(paddleClient, never()).removeScheduledCancellation(anyString());
    }

    @Test
    @DisplayName("resume throws NotFoundException when the org has no subscription record yet")
    void resume_shouldThrow_whenNoSubscriptionRecord() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resume(orgId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("resume throws ForbiddenException for a non-owner")
    void resume_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.resume(orgId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("resume throws NotFoundException when the caller is not a member")
    void resume_shouldThrow_whenCallerNotAMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resume(orgId, callerId))
                .isInstanceOf(NotFoundException.class);
    }

    // --- cancelPendingDowngrade ---

    @Test
    @DisplayName("cancelPendingDowngrade reverts Paddle's items to the active plan with do_not_bill and clears "
            + "the local pending state")
    void cancelPendingDowngrade_shouldRevertPaddleItems_andClearLocalPendingState() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID activeTierId = UUID.randomUUID();
        UUID activeAddonId = UUID.randomUUID();
        UUID pendingTierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123")
                .billingCycle("MONTHLY").tierId(activeTierId).addonIds(List.of(activeAddonId))
                .pendingTierId(pendingTierId).build();
        OrgSubscriptionModel updated = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123")
                .billingCycle("MONTHLY").tierId(activeTierId).addonIds(List.of(activeAddonId))
                .pendingTierId(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(activeTierId))
                .thenReturn(Optional.of(SubscriptionTierModel.builder().id(activeTierId).priceMonthly(30).build()));
        when(priceResolver.resolveTierPriceId(activeTierId, "MONTHLY")).thenReturn("pri_tier");
        when(priceResolver.resolveAddonPriceId(activeAddonId, "MONTHLY")).thenReturn("pri_addon");
        when(orgSubscriptionRepository.clearPendingDowngrade(subscriptionId, orgId)).thenReturn(updated);

        OrgSubscriptionModel result = service.cancelPendingDowngrade(orgId, ownerId);

        assertThat(result.getPendingTierId()).isNull();
        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), any(), eq("do_not_bill"));
        verify(orgSubscriptionRepository).clearPendingDowngrade(subscriptionId, orgId);
    }

    @Test
    @DisplayName("cancelPendingDowngrade throws ConflictException when there's no pending downgrade to cancel")
    void cancelPendingDowngrade_shouldThrow_whenNoPendingDowngrade() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123")
                .pendingTierId(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.cancelPendingDowngrade(orgId, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Paddle.NO_PENDING_DOWNGRADE_TO_CANCEL);
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("cancelPendingDowngrade throws ConflictException when there's no Paddle subscription yet")
    void cancelPendingDowngrade_shouldThrow_whenNoPaddleSubscriptionYet() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.cancelPendingDowngrade(orgId, ownerId))
                .isInstanceOf(ConflictException.class);
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("cancelPendingDowngrade throws BadRequestException for an internal org")
    void cancelPendingDowngrade_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.cancelPendingDowngrade(orgId, ownerId))
                .isInstanceOf(BadRequestException.class);
        verify(paddleClient, never()).updateSubscriptionItems(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("cancelPendingDowngrade throws NotFoundException when the org has no subscription record yet")
    void cancelPendingDowngrade_shouldThrow_whenNoSubscriptionRecord() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelPendingDowngrade(orgId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("cancelPendingDowngrade throws ForbiddenException for a non-owner")
    void cancelPendingDowngrade_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.cancelPendingDowngrade(orgId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("cancelPendingDowngrade throws NotFoundException when the caller is not a member")
    void cancelPendingDowngrade_shouldThrow_whenCallerNotAMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelPendingDowngrade(orgId, callerId))
                .isInstanceOf(NotFoundException.class);
    }

    // --- getBillingHistory ---

    @Test
    @DisplayName("getBillingHistory returns Paddle's transaction list for the org's Paddle customer")
    void getBillingHistory_shouldReturnTransactions() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        List<PaddleTransaction> transactions = List.of(
                new PaddleTransaction("txn_1", "completed", List.of(), Instant.parse("2026-08-01T00:00:00Z"),
                        "EUR", new PaddleTransactionDetails(new PaddleTransactionTotals("2900"), List.of())));

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.findPaddleCustomerId(orgId)).thenReturn(Optional.of("ctm_123"));
        when(paddleClient.listBillingHistory("ctm_123")).thenReturn(transactions);

        List<PaddleTransaction> result = service.getBillingHistory(orgId, ownerId);

        assertThat(result).isEqualTo(transactions);
        verify(paddleClient).listBillingHistory("ctm_123");
    }

    @Test
    @DisplayName("getBillingHistory returns an empty list when the org has no Paddle customer yet, rather than "
            + "an error")
    void getBillingHistory_shouldReturnEmptyList_whenNoPaddleCustomerYet() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(organisationRepository.findPaddleCustomerId(orgId)).thenReturn(Optional.empty());

        List<PaddleTransaction> result = service.getBillingHistory(orgId, ownerId);

        assertThat(result).isEmpty();
        verify(paddleClient, never()).listBillingHistory(anyString());
    }

    @Test
    @DisplayName("getBillingHistory throws BadRequestException for an internal org")
    void getBillingHistory_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.getBillingHistory(orgId, ownerId))
                .isInstanceOf(BadRequestException.class);
        verify(paddleClient, never()).listBillingHistory(anyString());
    }

    @Test
    @DisplayName("getBillingHistory throws NotFoundException when the organisation record is missing")
    void getBillingHistory_shouldThrow_whenOrgRecordMissing() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBillingHistory(orgId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getBillingHistory throws ForbiddenException for a non-owner")
    void getBillingHistory_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.getBillingHistory(orgId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getBillingHistory throws NotFoundException when the caller is not a member")
    void getBillingHistory_shouldThrow_whenCallerNotAMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBillingHistory(orgId, callerId))
                .isInstanceOf(NotFoundException.class);
    }

    // --- getUpdatePaymentMethodTransactionId ---

    @Test
    @DisplayName("getUpdatePaymentMethodTransactionId returns the transaction id Paddle provides")
    void getUpdatePaymentMethodTransactionId_shouldReturnTransactionId() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId("sub_123").build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(paddleClient.getUpdatePaymentMethodTransaction("sub_123"))
                .thenReturn(new PaddleTransaction("txn_123", "draft", List.of(), null, null, null));

        String result = service.getUpdatePaymentMethodTransactionId(orgId, ownerId);

        assertThat(result).isEqualTo("txn_123");
        verify(paddleClient).getUpdatePaymentMethodTransaction("sub_123");
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransactionId throws ConflictException when there's no Paddle subscription yet")
    void getUpdatePaymentMethodTransactionId_shouldThrow_whenNoPaddleSubscriptionYet() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleSubscriptionId(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.getUpdatePaymentMethodTransactionId(orgId, ownerId))
                .isInstanceOf(ConflictException.class);
        verify(paddleClient, never()).getUpdatePaymentMethodTransaction(anyString());
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransactionId throws BadRequestException for an internal org")
    void getUpdatePaymentMethodTransactionId_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.getUpdatePaymentMethodTransactionId(orgId, ownerId))
                .isInstanceOf(BadRequestException.class);
        verify(paddleClient, never()).getUpdatePaymentMethodTransaction(anyString());
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransactionId throws NotFoundException when the org has no subscription record yet")
    void getUpdatePaymentMethodTransactionId_shouldThrow_whenNoSubscriptionRecord() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUpdatePaymentMethodTransactionId(orgId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransactionId throws ForbiddenException for a non-owner")
    void getUpdatePaymentMethodTransactionId_shouldThrow_forNonOwner() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, memberId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> service.getUpdatePaymentMethodTransactionId(orgId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransactionId throws NotFoundException when the caller is not a member")
    void getUpdatePaymentMethodTransactionId_shouldThrow_whenCallerNotAMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUpdatePaymentMethodTransactionId(orgId, callerId))
                .isInstanceOf(NotFoundException.class);
    }

    // --- syncTierPriceToPaddle ---

    @Test
    @DisplayName("syncTierPriceToPaddle creates a Product and both Prices, and does not archive when no prior prices exist")
    void syncTierPriceToPaddle_shouldCreateProductAndPrices_whenNeverSyncedBefore() {
        UUID tierId = UUID.randomUUID();
        SubscriptionTierModel tier = SubscriptionTierModel.builder()
                .id(tierId).maxMembers(5).maxProjects(3).priceMonthly(24).priceAnnual(20).build();
        SubscriptionTierModel updated = SubscriptionTierModel.builder()
                .id(tierId).paddleProductId("pro_1").paddlePriceIdMonthly("pri_m").paddlePriceIdAnnual("pri_a").build();

        when(paddleClient.createProduct(anyString())).thenReturn(new PaddleProduct("pro_1", "Tier"));
        when(paddleClient.createPrice(eq("pro_1"), anyString(), eq("month"), eq("2400"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_m", "active"));
        when(paddleClient.createPrice(eq("pro_1"), anyString(), eq("year"), eq("2000"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_a", "active"));
        when(tierRepository.updatePaddleIds(tierId, "pro_1", "pri_m", "pri_a")).thenReturn(updated);

        SubscriptionTierModel result = service.syncTierPriceToPaddle(tier);

        assertThat(result.getPaddleProductId()).isEqualTo("pro_1");
        verify(paddleClient, never()).archivePrice(anyString());
        verify(tierRepository).updatePaddleIds(tierId, "pro_1", "pri_m", "pri_a");
    }

    @Test
    @DisplayName("syncTierPriceToPaddle reuses the existing Product and archives the old Prices after creating new ones")
    void syncTierPriceToPaddle_shouldReuseProduct_andArchiveOldPrices_whenAlreadySynced() {
        UUID tierId = UUID.randomUUID();
        SubscriptionTierModel tier = SubscriptionTierModel.builder()
                .id(tierId).maxMembers(5).maxProjects(3).priceMonthly(34).priceAnnual(28)
                .paddleProductId("pro_existing").paddlePriceIdMonthly("pri_old_m").paddlePriceIdAnnual("pri_old_a")
                .build();

        when(paddleClient.createPrice(eq("pro_existing"), anyString(), eq("month"), eq("3400"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_new_m", "active"));
        when(paddleClient.createPrice(eq("pro_existing"), anyString(), eq("year"), eq("2800"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_new_a", "active"));
        when(tierRepository.updatePaddleIds(tierId, "pro_existing", "pri_new_m", "pri_new_a"))
                .thenReturn(tier);

        service.syncTierPriceToPaddle(tier);

        verify(paddleClient, never()).createProduct(anyString());
        verify(paddleClient).archivePrice("pri_old_m");
        verify(paddleClient).archivePrice("pri_old_a");
        verify(tierRepository).updatePaddleIds(tierId, "pro_existing", "pri_new_m", "pri_new_a");
    }

    // --- syncAddonPriceToPaddle ---

    @Test
    @DisplayName("syncAddonPriceToPaddle creates a Product and both Prices, and does not archive when no prior prices exist")
    void syncAddonPriceToPaddle_shouldCreateProductAndPrices_whenNeverSyncedBefore() {
        UUID addonId = UUID.randomUUID();
        SubscriptionAddonModel addon = SubscriptionAddonModel.builder()
                .id(addonId).key("DASHBOARD").name("Dashboard").priceMonthly(9).priceAnnual(8).build();
        SubscriptionAddonModel updated = SubscriptionAddonModel.builder()
                .id(addonId).paddleProductId("pro_1").paddlePriceIdMonthly("pri_m").paddlePriceIdAnnual("pri_a").build();

        when(paddleClient.createProduct("Dashboard")).thenReturn(new PaddleProduct("pro_1", "Dashboard"));
        when(paddleClient.createPrice(eq("pro_1"), anyString(), eq("month"), eq("900"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_m", "active"));
        when(paddleClient.createPrice(eq("pro_1"), anyString(), eq("year"), eq("800"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_a", "active"));
        when(addonRepository.updatePaddleIds(addonId, "pro_1", "pri_m", "pri_a")).thenReturn(updated);

        SubscriptionAddonModel result = service.syncAddonPriceToPaddle(addon);

        assertThat(result.getPaddleProductId()).isEqualTo("pro_1");
        verify(paddleClient, never()).archivePrice(anyString());
        verify(addonRepository).updatePaddleIds(addonId, "pro_1", "pri_m", "pri_a");
    }

    @Test
    @DisplayName("syncAddonPriceToPaddle reuses the existing Product and archives the old Prices after creating new ones")
    void syncAddonPriceToPaddle_shouldReuseProduct_andArchiveOldPrices_whenAlreadySynced() {
        UUID addonId = UUID.randomUUID();
        SubscriptionAddonModel addon = SubscriptionAddonModel.builder()
                .id(addonId).key("DASHBOARD").name("Dashboard").priceMonthly(9).priceAnnual(8)
                .paddleProductId("pro_existing").paddlePriceIdMonthly("pri_old_m").paddlePriceIdAnnual("pri_old_a")
                .build();

        when(paddleClient.createPrice(eq("pro_existing"), anyString(), eq("month"), eq("900"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_new_m", "active"));
        when(paddleClient.createPrice(eq("pro_existing"), anyString(), eq("year"), eq("800"), eq("EUR")))
                .thenReturn(new PaddlePrice("pri_new_a", "active"));
        when(addonRepository.updatePaddleIds(addonId, "pro_existing", "pri_new_m", "pri_new_a"))
                .thenReturn(addon);

        service.syncAddonPriceToPaddle(addon);

        verify(paddleClient, never()).createProduct(anyString());
        verify(paddleClient).archivePrice("pri_old_m");
        verify(paddleClient).archivePrice("pri_old_a");
        verify(addonRepository).updatePaddleIds(addonId, "pro_existing", "pri_new_m", "pri_new_a");
    }

    // --- syncCatalogToPaddle ---

    @Test
    @DisplayName("syncCatalogToPaddle only syncs tiers/addons that have never been synced, and counts them")
    void syncCatalogToPaddle_shouldOnlySyncUnsyncedRows_andReturnCounts() {
        UUID unsyncedTierId = UUID.randomUUID();
        UUID syncedTierId = UUID.randomUUID();
        UUID unsyncedAddonId = UUID.randomUUID();
        UUID syncedAddonId = UUID.randomUUID();

        SubscriptionTierModel unsyncedTier = SubscriptionTierModel.builder()
                .id(unsyncedTierId).maxMembers(5).priceMonthly(24).priceAnnual(20).build();
        SubscriptionTierModel syncedTier = SubscriptionTierModel.builder()
                .id(syncedTierId).maxMembers(10).priceMonthly(44).priceAnnual(37).paddleProductId("pro_synced").build();
        SubscriptionAddonModel unsyncedAddon = SubscriptionAddonModel.builder()
                .id(unsyncedAddonId).key("NOTES").name("Notes").priceMonthly(9).priceAnnual(8).build();
        SubscriptionAddonModel syncedAddon = SubscriptionAddonModel.builder()
                .id(syncedAddonId).key("DASHBOARD").name("Dashboard").priceMonthly(9).priceAnnual(8)
                .paddleProductId("pro_synced").build();

        when(tierRepository.findAll()).thenReturn(List.of(unsyncedTier, syncedTier));
        when(addonRepository.findAll()).thenReturn(List.of(unsyncedAddon, syncedAddon));
        when(paddleClient.createProduct(anyString())).thenReturn(new PaddleProduct("pro_new", "New"));
        when(paddleClient.createPrice(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PaddlePrice("pri_new", "active"));
        when(tierRepository.updatePaddleIds(eq(unsyncedTierId), any(), any(), any())).thenReturn(unsyncedTier);
        when(addonRepository.updatePaddleIds(eq(unsyncedAddonId), any(), any(), any())).thenReturn(unsyncedAddon);

        PaddleCatalogSyncResult result = service.syncCatalogToPaddle();

        assertThat(result.tiersSynced()).isEqualTo(1);
        assertThat(result.addonsSynced()).isEqualTo(1);
        verify(tierRepository).updatePaddleIds(eq(unsyncedTierId), any(), any(), any());
        verify(tierRepository, never()).updatePaddleIds(eq(syncedTierId), any(), any(), any());
        verify(addonRepository).updatePaddleIds(eq(unsyncedAddonId), any(), any(), any());
        verify(addonRepository, never()).updatePaddleIds(eq(syncedAddonId), any(), any(), any());
    }
}
