package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleDiscount;
import com.opsclear.paddle.PaddleSubscription;
import com.opsclear.repository.OrgCreditRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.opsclear.generated.jooq.Tables.USERS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolated from {@link FeedbackAndCreditsIntegrationTest} deliberately: this is the
 * credit-grant Paddle-sync scenario in the suite that mocks {@link PaddleClient}
 * rather than hitting the real sandbox (JOB-180) — a genuine Paddle transport failure
 * can't be produced on demand against a real API, and mocking is the only way to prove
 * the transaction actually rolls back end-to-end (a real DB, not a Mockito assertion
 * that an exception was merely thrown) or that the success path calls both Paddle
 * methods with the right arguments. Sharing a class with the real-sandbox tests would
 * mean this mock leaks into them too, so it gets its own Spring context instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Credit grant — Paddle discount sync (mocked PaddleClient)")
class CreditGrantPaddleSyncIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgCreditRepository orgCreditRepository;
    @Autowired private OrgSubscriptionRepository orgSubscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @MockitoBean private PaddleClient paddleClient;

    private UUID superUserId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        // users first: TRUNCATE ... CASCADE (see UserRepository.deleteAll) clears the
        // entire FK graph transitively, so it must run before the narrower deletes
        // below — otherwise a project left behind by another test class can block
        // organisationRepository.deleteAll().
        userRepository.deleteAll();
        orgCreditRepository.deleteAll();
        orgSubscriptionRepository.deleteAll();
        organisationRepository.deleteAll();

        superUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(superUserId).email("super@example.com").name("Super").build());
        dsl.update(USERS).set(USERS.SUPER_USER, true).where(USERS.ID.eq(superUserId)).execute();

        UUID ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("RLB").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);

        // A real, webhook-confirmed subscription (not just a Paddle customer) is what
        // syncCreditToPaddle now checks for (JOB-180 — Discounts attach to the
        // subscription, not the customer).
        UUID tierId = tierRepository.findAll().getFirst().getId();
        orgSubscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of());
        orgSubscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_rollback", "ACTIVE", null, null);
    }

    @Test
    @DisplayName("grantCredit_shouldReturn502AndRollBack_whenPaddleCallFails")
    void grantCredit_shouldReturn502AndRollBack_whenPaddleCallFails() throws Exception {
        when(paddleClient.createOneTimeDiscount(any(), any(), any()))
                .thenThrow(new RestClientException("429 Too Many Requests"));

        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orgId", orgId, "amount", 500, "reason", "Should roll back"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Bad Gateway"));

        assertThat(orgCreditRepository.findByOrgId(orgId)).isEmpty();
    }

    @Test
    @DisplayName("grantCredit_shouldReturn201_andAttachDiscount_whenPaddleSyncSucceeds")
    void grantCredit_shouldReturn201_andAttachDiscount_whenPaddleSyncSucceeds() throws Exception {
        when(paddleClient.createOneTimeDiscount("2900", "EUR", "OpsClear credit: Great bug report"))
                .thenReturn(new PaddleDiscount("dsc_test_123"));
        when(paddleClient.attachDiscountToSubscription("sub_test_rollback", "dsc_test_123"))
                .thenReturn(new PaddleSubscription("sub_test_rollback", "active", "ctm_test", null));

        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orgId", orgId, "amount", 29, "reason", "Great bug report"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(29))
                .andExpect(jsonPath("$.paddleSyncSkippedReason").doesNotExist());

        verify(paddleClient).createOneTimeDiscount("2900", "EUR", "OpsClear credit: Great bug report");
        verify(paddleClient).attachDiscountToSubscription("sub_test_rollback", "dsc_test_123");
        assertThat(orgCreditRepository.findByOrgId(orgId)).hasSize(1);
    }
}
