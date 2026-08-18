package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.repository.OrgCreditRepository;
import com.opsclear.repository.OrganisationRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static com.opsclear.generated.jooq.Tables.USERS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolated from {@link FeedbackAndCreditsIntegrationTest} deliberately: this is the
 * one credit-grant scenario in the suite that mocks {@link PaddleClient} rather than
 * hitting the real sandbox (JOB-180) — a genuine Paddle transport failure can't be
 * produced on demand against a real API, and this is also the only way to prove the
 * transaction actually rolls back end-to-end (a real DB, not a Mockito assertion that
 * an exception was merely thrown). Sharing a class with the real-sandbox tests would
 * mean this mock leaks into them too, so it gets its own Spring context instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Credit grant — rollback on a genuine Paddle sync failure")
class CreditGrantRollbackIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgCreditRepository orgCreditRepository;
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
        organisationRepository.updatePaddleCustomerId(orgId, "ctm_test_rollback");
    }

    @Test
    @DisplayName("grantCredit_shouldReturn502AndRollBack_whenPaddleCallFails")
    void grantCredit_shouldReturn502AndRollBack_whenPaddleCallFails() throws Exception {
        when(paddleClient.findLatestCompletedTransaction(any()))
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
}
