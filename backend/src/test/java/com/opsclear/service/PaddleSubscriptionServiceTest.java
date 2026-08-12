package com.opsclear.service;

import com.opsclear.dto.UpdatePaddleSubscriptionRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleCustomer;
import com.opsclear.paddle.PaddlePriceResolver;
import com.opsclear.paddle.PaddleSubscription;
import com.opsclear.paddle.PaddleSubscriptionItem;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private PaddleClient paddleClient;
    @Mock private PaddlePriceResolver priceResolver;

    private PaddleSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PaddleSubscriptionService(
                orgSubscriptionRepository, organisationRepository, userRepository, tierRepository,
                paddleClient, priceResolver);
    }

    // --- initiate ---

    @Test
    @DisplayName("initiate creates a Paddle customer and persists its id")
    void initiate_shouldCreatePaddleCustomer_andPersistId() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).build();
        OrgSubscriptionModel updated = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false).paddleCustomerId("ctm_123").build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.of(UserModel.builder().id(ownerId).email("owner@example.com").build()));
        when(paddleClient.createCustomer("owner@example.com", "Acme Corp"))
                .thenReturn(new PaddleCustomer("ctm_123", "owner@example.com"));
        when(orgSubscriptionRepository.updatePaddleCustomerId(subscriptionId, orgId, "ctm_123"))
                .thenReturn(updated);

        OrgSubscriptionModel result = service.initiate(orgId, ownerId);

        assertThat(result.getPaddleCustomerId()).isEqualTo("ctm_123");
        verify(orgSubscriptionRepository).updatePaddleCustomerId(subscriptionId, orgId, "ctm_123");
    }

    @Test
    @DisplayName("initiate is idempotent when the org already has a Paddle customer")
    void initiate_shouldSkipCreation_whenCustomerAlreadyExists() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).paddleCustomerId("ctm_existing").build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));

        OrgSubscriptionModel result = service.initiate(orgId, ownerId);

        assertThat(result.getPaddleCustomerId()).isEqualTo("ctm_existing");
        verify(paddleClient, never()).createCustomer(anyString(), anyString());
        verify(orgSubscriptionRepository, never()).updatePaddleCustomerId(any(), any(), anyString());
    }

    @Test
    @DisplayName("initiate throws BadRequestException for an internal org")
    void initiate_shouldThrow_whenOrgIsInternal() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(true).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
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
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
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
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).isInternal(false).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Acme Corp").build()));
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

    @Test
    @DisplayName("initiate throws NotFoundException when the org has no subscription record yet")
    void initiate_shouldThrow_whenNoSubscriptionRecord() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(orgId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No subscription found for this organisation — select a tier first");
    }

    // --- updateSubscriptionItems ---

    @Test
    @DisplayName("updateSubscriptionItems resolves price ids, calls Paddle, and updates the local record")
    void updateSubscriptionItems_shouldSyncPaddle_andUpdateLocalRecord() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        UUID addonId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false)
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(Set.of(addonId)).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).build()));
        when(priceResolver.resolveTierPriceId(tierId)).thenReturn("pri_tier");
        when(priceResolver.resolveAddonPriceId(addonId)).thenReturn("pri_addon");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123"));

        PaddleSubscription result = service.updateSubscriptionItems(orgId, ownerId, request);

        assertThat(result.status()).isEqualTo("active");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaddleSubscriptionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), itemsCaptor.capture(), eq("prorated_immediately"));
        assertThat(itemsCaptor.getValue()).containsExactlyInAnyOrder(
                new PaddleSubscriptionItem("pri_tier", 1), new PaddleSubscriptionItem("pri_addon", 1));

        verify(orgSubscriptionRepository).update(subscriptionId, orgId, tierId, "MONTHLY", Set.of(addonId));
    }

    @Test
    @DisplayName("updateSubscriptionItems sends only the tier item when addonIds is null")
    void updateSubscriptionItems_shouldSendTierOnly_whenAddonIdsIsNull() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();

        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(orgId).isInternal(false)
                .paddleSubscriptionId("sub_123").billingCycle("MONTHLY").build();
        UpdatePaddleSubscriptionRequest request = UpdatePaddleSubscriptionRequest.builder()
                .tierId(tierId).addonIds(null).build();

        when(organisationRepository.findMemberRole(orgId, ownerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(subscription));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(SubscriptionTierModel.builder().id(tierId).build()));
        when(priceResolver.resolveTierPriceId(tierId)).thenReturn("pri_tier");
        when(paddleClient.updateSubscriptionItems(eq("sub_123"), any(), eq("prorated_immediately")))
                .thenReturn(new PaddleSubscription("sub_123", "active", "ctm_123"));

        service.updateSubscriptionItems(orgId, ownerId, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaddleSubscriptionItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paddleClient).updateSubscriptionItems(eq("sub_123"), itemsCaptor.capture(), eq("prorated_immediately"));
        assertThat(itemsCaptor.getValue()).containsExactly(new PaddleSubscriptionItem("pri_tier", 1));

        verify(orgSubscriptionRepository).update(subscriptionId, orgId, tierId, "MONTHLY", Set.of());
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
}
