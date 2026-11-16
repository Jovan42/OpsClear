package com.opsclear.integration;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code opsclear.rate-limit.enabled=false} turns the per-API-key limit off (ADR-0058, JOB-337), even with a tiny
 * bucket configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "opsclear.rate-limit.enabled=false",
    "opsclear.rate-limit.api-key.capacity=3",
    "opsclear.rate-limit.api-key.refill-per-minute=1"
})
@DisplayName("API key rate limit, disabled")
class ApiKeyRateLimitDisabledIntegrationTest {

    private static final int CAPACITY = 3;

    @Autowired private MockMvc mockMvc;
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
        userRepository.save(UserModel.builder().id(userId).email("user@example.com").name("User").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Customer Org").slug("CUS").createdBy(userId).build());
        organisationRepository.saveMember(org.getId(), userId, OrganisationRole.OWNER);
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(org.getId(), tierId, "MONTHLY", Set.of());
    }

    @Test
    @DisplayName("With the limit disabled, API key requests past the capacity should all succeed")
    void apiKeyRequest_whenDisabled_shouldNeverBeLimited() throws Exception {
        String rawKey = "opck_" + UUID.randomUUID().toString().replace("-", "");
        apiKeyRepository.save(ApiKeyModel.builder()
                .userId(userId)
                .name("unlimited key")
                .keyHash(ApiKeyService.sha256(rawKey))
                .keyPrefix(rawKey.substring(0, 12))
                .build());

        for (int i = 0; i < CAPACITY * 4; i++) {
            mockMvc.perform(get(ApiPaths.PROJECTS).header("X-Api-Key", rawKey))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Retry-After"));
        }
    }
}
