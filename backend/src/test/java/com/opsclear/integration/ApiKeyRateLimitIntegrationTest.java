package com.opsclear.integration;

import com.jayway.jsonpath.JsonPath;
import com.opsclear.model.ApiKeyModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApiKeyRepository;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.ApiKeyService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-API-key rate limit through the real filter chain (ADR-0058, JOB-337). A capacity of 3 keeps the tests short;
 * a refill of 1 per minute means no token comes back while a test runs, so the results don't depend on timing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "opsclear.rate-limit.enabled=true",
    "opsclear.rate-limit.api-key.capacity=3",
    "opsclear.rate-limit.api-key.refill-per-minute=1"
})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("API key rate limit")
class ApiKeyRateLimitIntegrationTest {

    private static final int CAPACITY = 3;

    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private UUID internalUserId;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        internalUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(userId).email("user@example.com").name("User").build());
        userRepository.save(UserModel.builder()
                .id(internalUserId).email("internal@example.com").name("Internal").build());

        UUID tierId = tierRepository.findAll().getFirst().getId();

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Customer Org").slug("CUS").createdBy(userId).build());
        organisationRepository.saveMember(org.getId(), userId, OrganisationRole.OWNER);
        subscriptionRepository.create(org.getId(), tierId, "MONTHLY", Set.of());

        OrganisationModel internalOrg = organisationRepository.save(
                OrganisationModel.builder().name("Internal Org").slug("INT").createdBy(internalUserId).build());
        organisationRepository.saveMember(internalOrg.getId(), internalUserId, OrganisationRole.OWNER);
        UUID internalOrgId = internalOrg.getId();
        subscriptionRepository.create(internalOrgId, tierId, "MONTHLY", Set.of());
        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.IS_INTERNAL, true)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(internalOrgId))
                .execute();
    }

    @Test
    @DisplayName("The request over capacity should get 429 with Retry-After and the JSON error body")
    void apiKeyRequest_overCapacity_shouldReturn429WithRetryAfterAndJsonBody() throws Exception {
        String rawKey = createKey(userId, "limited key");

        for (int i = 0; i < CAPACITY; i++) {
            getProjects(rawKey).andExpect(status().isOk());
        }

        MvcResult rejected = getProjects(rawKey)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andReturn();

        long retryAfter = Long.parseLong(rejected.getResponse().getHeader("Retry-After"));
        // Refill is 1/min, so the next token is (almost) a full minute away.
        assertThat(retryAfter).isBetween(1L, 60L);
        String body = rejected.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.message"))
                .isEqualTo("Rate limit exceeded. Try again in " + retryAfter + " second(s).");
    }

    @Test
    @DisplayName("A rejected request should not update the key's lastUsedAt")
    void apiKeyRequest_overCapacity_shouldNotUpdateLastUsedAt() throws Exception {
        String rawKey = createKey(userId, "last used key");
        UUID keyId = apiKeyRepository.findByKeyHash(ApiKeyService.sha256(rawKey)).orElseThrow().getId();

        for (int i = 0; i < CAPACITY; i++) {
            getProjects(rawKey).andExpect(status().isOk());
        }
        Instant lastUsedBefore = apiKeyRepository.findById(keyId).orElseThrow().getLastUsedAt();
        assertThat(lastUsedBefore).isNotNull();

        getProjects(rawKey).andExpect(status().isTooManyRequests());

        assertThat(apiKeyRepository.findById(keyId).orElseThrow().getLastUsedAt()).isEqualTo(lastUsedBefore);
    }

    @Test
    @DisplayName("Repeated rejections of a key should log one WARN a minute, with the key prefix only")
    void apiKeyRequest_repeatedRejections_shouldLogOneWarnWithPrefixOnly(CapturedOutput output) throws Exception {
        String rawKey = createKey(userId, "runaway key");
        String prefix = rawKey.substring(0, 12);

        for (int i = 0; i < CAPACITY; i++) {
            getProjects(rawKey).andExpect(status().isOk());
        }
        getProjects(rawKey).andExpect(status().isTooManyRequests());
        getProjects(rawKey).andExpect(status().isTooManyRequests());
        getProjects(rawKey).andExpect(status().isTooManyRequests());

        String warning = "Rate limit exceeded for API key '" + prefix + "'";
        assertThat(output.getAll().split(Pattern.quote(warning), -1)).hasSize(2);
        assertThat(output.getAll()).doesNotContain(rawKey);
    }

    @Test
    @DisplayName("A second key should keep its own bucket")
    void apiKeyRequest_secondKey_shouldHaveItsOwnBucket() throws Exception {
        String firstKey = createKey(userId, "first key");
        String secondKey = createKey(userId, "second key");

        for (int i = 0; i < CAPACITY; i++) {
            getProjects(firstKey).andExpect(status().isOk());
        }
        getProjects(firstKey).andExpect(status().isTooManyRequests());

        for (int i = 0; i < CAPACITY; i++) {
            getProjects(secondKey).andExpect(status().isOk());
        }
        getProjects(secondKey).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("A key of an internal org should never be limited")
    void apiKeyRequest_internalOrgKey_shouldNeverBeLimited() throws Exception {
        String rawKey = createKey(internalUserId, "internal key");

        for (int i = 0; i < CAPACITY * 4; i++) {
            getProjects(rawKey).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("JWT requests should not be limited")
    void jwtRequest_shouldNotBeLimited() throws Exception {
        for (int i = 0; i < CAPACITY * 4; i++) {
            mockMvc.perform(get(ApiPaths.PROJECTS)
                            .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("An invalid key should still get 401, not 429, however often it is sent")
    void apiKeyRequest_invalidKey_shouldGet401NotRateLimited() throws Exception {
        for (int i = 0; i < CAPACITY * 2; i++) {
            getProjects("opck_not_a_real_key").andExpect(status().isUnauthorized());
        }
    }

    private ResultActions getProjects(String rawKey) throws Exception {
        return mockMvc.perform(get(ApiPaths.PROJECTS).header("X-Api-Key", rawKey));
    }

    private String createKey(UUID ownerId, String name) {
        String rawKey = "opck_" + UUID.randomUUID().toString().replace("-", "");
        apiKeyRepository.save(ApiKeyModel.builder()
                .userId(ownerId)
                .name(name)
                .keyHash(ApiKeyService.sha256(rawKey))
                .keyPrefix(rawKey.substring(0, 12))
                .build());
        return rawKey;
    }
}
