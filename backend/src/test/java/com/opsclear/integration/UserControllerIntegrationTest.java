package com.opsclear.integration;

import com.jayway.jsonpath.JsonPath;
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
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("User search endpoint")
class UserControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID callerId;
    private OrganisationModel org;
    private OrganisationModel otherOrg;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        callerId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(callerId).email("caller@example.com").name("Caller").build());

        org = organisationRepository.save(OrganisationModel.builder()
                .name("Test Org").slug("TST").createdBy(callerId).build());
        organisationRepository.saveMember(org.getId(), callerId, OrganisationRole.OWNER);

        UUID otherOwnerId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(otherOwnerId).email("other-owner@example.com").name("Other Owner").build());
        otherOrg = organisationRepository.save(OrganisationModel.builder()
                .name("Other Org").slug("OTH").createdBy(otherOwnerId).build());
        organisationRepository.saveMember(otherOrg.getId(), otherOwnerId, OrganisationRole.OWNER);
    }

    private RequestPostProcessor callerJwt() {
        return jwt().jwt(j -> j.subject(callerId.toString()).claim("email", "caller@example.com"));
    }

    private UUID saveUser(String email, String name) {
        UUID id = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(id).email(email).name(name).build());
        return id;
    }

    private UUID saveMember(OrganisationModel organisation, String email, String name) {
        UUID id = saveUser(email, name);
        organisationRepository.saveMember(organisation.getId(), id, OrganisationRole.MEMBER);
        return id;
    }

    // --- Organisation scope (JOB-400) ---

    @Test
    @DisplayName("search_shouldNeverReturnUsersOutsideCallerOrg_evenOnExactEmailMatch")
    void search_shouldNeverReturnUsersOutsideCallerOrg_evenOnExactEmailMatch() throws Exception {
        saveMember(org, "alice@example.com", "Alice");
        saveMember(otherOrg, "alicia@other.com", "Alicia Other");
        saveUser("alina@noorg.com", "Alina No Org");

        mockMvc.perform(get(ApiPaths.usersSearch("ali")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));

        mockMvc.perform(get(ApiPaths.usersSearch("alicia@other.com")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(ApiPaths.usersSearch("Other")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("search_shouldScopeTheDeprecatedEmailAlias_toCallerOrgToo")
    void search_shouldScopeTheDeprecatedEmailAlias_toCallerOrgToo() throws Exception {
        saveMember(org, "alice@example.com", "Alice");
        saveMember(otherOrg, "alicia@other.com", "Alicia Other");

        mockMvc.perform(get(ApiPaths.usersSearchByEmailAlias("ali")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    @DisplayName("search_shouldScopeToCallerOrg_whenAuthenticatedWithApiKey")
    void search_shouldScopeToCallerOrg_whenAuthenticatedWithApiKey() throws Exception {
        saveMember(org, "nikola@example.com", "Nikola Petrovic");
        saveMember(otherOrg, "nikolina@other.com", "Nikolina Other");

        UUID apiKeysAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("API_KEYS")).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(org.getId(), tierId, "MONTHLY", Set.of(apiKeysAddonId));
        subscriptionRepository.updateFromPaddleWebhook(org.getId(), "sub_test_" + org.getId(), "ACTIVE", null, null);

        String body = mockMvc.perform(post(ApiPaths.API_KEYS).with(callerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "search key"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String rawKey = JsonPath.read(body, "$.key");

        mockMvc.perform(get(ApiPaths.usersSearch("nikol")).header("X-Api-Key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("nikola@example.com"));
    }

    // --- Matching ---

    @Test
    @DisplayName("search_shouldMatchName_caseInsensitiveContains")
    void search_shouldMatchName_caseInsensitiveContains() throws Exception {
        UUID nikolaId = saveMember(org, "np@example.com", "Nikola Petrovic");
        saveMember(org, "ana@example.com", "Ana Jovanovic");

        mockMvc.perform(get(ApiPaths.usersSearch("kola")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(nikolaId.toString()));

        mockMvc.perform(get(ApiPaths.usersSearch("PETRO")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Nikola Petrovic"));
    }

    @Test
    @DisplayName("search_shouldMatchEmailByPrefixOnly")
    void search_shouldMatchEmailByPrefixOnly() throws Exception {
        saveMember(org, "alice@example.com", "Alice");
        saveMember(org, "bob.ali@example.com", "Bob");

        mockMvc.perform(get(ApiPaths.usersSearch("ali")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    @DisplayName("search_shouldBeCaseInsensitive_onEmail")
    void search_shouldBeCaseInsensitive_onEmail() throws Exception {
        saveMember(org, "Alice@Example.COM", "Alice");

        mockMvc.perform(get(ApiPaths.usersSearch("ALICE@ex")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice"));
    }

    @Test
    @DisplayName("search_shouldTreatLikeWildcardsAsLiteralCharacters")
    void search_shouldTreatLikeWildcardsAsLiteralCharacters() throws Exception {
        saveMember(org, "alice@example.com", "Alice");

        mockMvc.perform(get(ApiPaths.USERS).param("q", "%_").with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("search_shouldIgnoreSurroundingWhitespace_inQuery")
    void search_shouldIgnoreSurroundingWhitespace_inQuery() throws Exception {
        saveMember(org, "np@example.com", "Nikola Petrovic");

        mockMvc.perform(get(ApiPaths.USERS).param("q", "  Nikola ").with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("search_shouldPreferQ_whenBothQAndEmailAreSent")
    void search_shouldPreferQ_whenBothQAndEmailAreSent() throws Exception {
        saveMember(org, "alice@example.com", "Alice");
        saveMember(org, "bob@example.com", "Bob");

        mockMvc.perform(get(ApiPaths.USERS).param("q", "bob").param("email", "alice").with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("bob@example.com"));
    }

    @Test
    @DisplayName("search_shouldReturnEmpty_whenNoOrgMemberMatches")
    void search_shouldReturnEmpty_whenNoOrgMemberMatches() throws Exception {
        mockMvc.perform(get(ApiPaths.usersSearch("zzz")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("search_shouldLimitResultsToTen")
    void search_shouldLimitResultsToTen() throws Exception {
        for (int i = 0; i < 15; i++) {
            saveMember(org, "user" + i + "@example.com", "User " + i);
        }

        mockMvc.perform(get(ApiPaths.usersSearch("user")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    @DisplayName("search_shouldReturnIdNameEmail_inResponse")
    void search_shouldReturnIdNameEmail_inResponse() throws Exception {
        UUID targetId = saveMember(org, "jane@example.com", "Jane Doe");

        mockMvc.perform(get(ApiPaths.usersSearch("jane")).with(callerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(targetId.toString()))
                .andExpect(jsonPath("$[0].name").value("Jane Doe"))
                .andExpect(jsonPath("$[0].email").value("jane@example.com"));
    }

    // --- Validation and access ---

    @Test
    @DisplayName("search_shouldReturn400_whenQueryTooShort")
    void search_shouldReturn400_whenQueryTooShort() throws Exception {
        mockMvc.perform(get(ApiPaths.usersSearch("a")).with(callerJwt()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiPaths.usersSearchByEmailAlias("a")).with(callerJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search_shouldReturn400_whenQueryBlankAfterTrimming")
    void search_shouldReturn400_whenQueryBlankAfterTrimming() throws Exception {
        mockMvc.perform(get(ApiPaths.USERS).param("q", "  a  ").with(callerJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Search query must be at least 2 characters"));
        mockMvc.perform(get(ApiPaths.USERS).param("email", " ").with(callerJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search_shouldReturn400_whenNoQueryParamIsSent")
    void search_shouldReturn400_whenNoQueryParamIsSent() throws Exception {
        mockMvc.perform(get(ApiPaths.USERS).with(callerJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search_shouldReturn401_withoutAuthentication")
    void search_shouldReturn401_withoutAuthentication() throws Exception {
        mockMvc.perform(get(ApiPaths.usersSearch("ali")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("search_shouldReturn403_whenCallerBelongsToNoOrg")
    void search_shouldReturn403_whenCallerBelongsToNoOrg() throws Exception {
        UUID outsiderId = saveUser("outsider@example.com", "Outsider");

        mockMvc.perform(get(ApiPaths.usersSearch("ali"))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }
}
