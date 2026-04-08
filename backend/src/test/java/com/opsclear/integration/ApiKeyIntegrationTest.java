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
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.opsclear.service.ApiKeyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyIntegrationTest {

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
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private UUID otherUserId;

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
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(userId).email("user@example.com").name("User").build());
        userRepository.save(UserModel.builder().id(otherUserId).email("other@example.com").name("Other").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(userId).build());
        organisationRepository.saveMember(org.getId(), userId, OrganisationRole.OWNER);
    }

    // --- POST /api/user/api-keys ---

    @Test
    @DisplayName("Should create an API key and return 201 with raw key")
    void createApiKey_shouldReturn201_withRawKey() throws Exception {
        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "deploy script"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("opck_")))
                .andExpect(jsonPath("$.keyPrefix").value(org.hamcrest.Matchers.startsWith("opck_")))
                .andExpect(jsonPath("$.name").value("deploy script"))
                .andExpect(jsonPath("$.id").isString());
    }

    @Test
    @DisplayName("Should return 400 when name is blank on create")
    void createApiKey_shouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 when unauthenticated on create")
    void createApiKey_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "script"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/user/api-keys ---

    @Test
    @DisplayName("Should list only the current user's active API keys")
    void listApiKeys_shouldReturnOnlyCurrentUsersKeys() throws Exception {
        // Create two keys for current user and one for other user
        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "key1"}
                                """));

        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "key2"}
                                """));

        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(otherUserId.toString()).claim("email", "other@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "other key"}
                                """));

        mockMvc.perform(get(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Should not expose raw key or hash in list response")
    void listApiKeys_shouldNotExposeRawKeyOrHash() throws Exception {
        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "script"}
                                """));

        mockMvc.perform(get(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$[0].key").doesNotExist())
                .andExpect(jsonPath("$[0].keyPrefix").isString());
    }

    // --- DELETE /api/user/api-keys/{id} ---

    @Test
    @DisplayName("Should revoke an API key and return 204")
    void revokeApiKey_shouldReturn204_andKeyDisappearsFromList() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "to revoke"}
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String keyId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete(ApiPaths.apiKey(UUID.fromString(keyId)))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Should return 404 when revoking a key belonging to another user")
    void revokeApiKey_shouldReturn404_whenKeyBelongsToOtherUser() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(otherUserId.toString()).claim("email", "other@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "other key"}
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String keyId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete(ApiPaths.apiKey(UUID.fromString(keyId)))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when revoking an already-revoked key")
    void revokeApiKey_shouldReturn404_whenKeyAlreadyRevoked() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "revoke twice"}
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String keyId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete(ApiPaths.apiKey(UUID.fromString(keyId)))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(ApiPaths.apiKey(UUID.fromString(keyId)))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when revoking a non-existent key")
    void revokeApiKey_shouldReturn404_whenKeyNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.apiKey(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should create a key with expiresAt set")
    void createApiKey_shouldPersistExpiresAt_whenProvided() throws Exception {
        mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "expiring key", "expiresAt": "2030-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("Revoked key should report isRevoked true and isActive false")
    void revokeApiKey_shouldMakeKeyRevokedAndInactive() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "check active"}
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String keyId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete(ApiPaths.apiKey(UUID.fromString(keyId)))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isNoContent());

        apiKeyRepository.findById(UUID.fromString(keyId)).ifPresent(key -> {
            assertThat(key.isRevoked()).isTrue();
            assertThat(key.isActive()).isFalse();
            assertThat(key.isExpired()).isFalse();
        });
    }

    @Test
    @DisplayName("Key with future expiresAt should not be expired and should be active")
    void createApiKey_withFutureExpiry_shouldNotBeExpired() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "future expiry", "expiresAt": "2099-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String keyId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        apiKeyRepository.findById(UUID.fromString(keyId)).ifPresent(key -> {
            assertThat(key.isExpired()).isFalse();
            assertThat(key.isActive()).isTrue();
            assertThat(key.getExpiresAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Key with past expiresAt should be expired and inactive")
    void apiKeyModel_withPastExpiry_shouldBeExpiredAndInactive() {
        ApiKeyModel key = apiKeyRepository.save(ApiKeyModel.builder()
                .userId(userId)
                .name("expired key")
                .keyHash("deadbeef".repeat(8))
                .keyPrefix("opck_dead")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());

        assertThat(key.isExpired()).isTrue();
        assertThat(key.isActive()).isFalse();
        assertThat(key.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("revoke() model method should set revokedAt")
    void apiKeyModel_revoke_shouldSetRevokedAt() {
        ApiKeyModel key = apiKeyRepository.save(ApiKeyModel.builder()
                .userId(userId)
                .name("revoke model test")
                .keyHash("cafebabe".repeat(8))
                .keyPrefix("opck_cafe")
                .build());

        assertThat(key.isRevoked()).isFalse();
        key.revoke();
        assertThat(key.isRevoked()).isTrue();
        assertThat(key.getRevokedAt()).isNotNull();
    }

    // --- ApiKeyAuthFilter ---

    @Test
    @DisplayName("Should authenticate and return 200 using valid API key header")
    void apiKeyFilter_shouldAuthenticate_withValidKey() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "filter test key"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String rawKey = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.key");

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .header("X-Api-Key", rawKey))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should fall through to JWT auth and return 401 when X-Api-Key header is blank")
    void apiKeyFilter_shouldPassThrough_whenKeyHeaderIsBlank() throws Exception {
        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .header("X-Api-Key", "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 when API key is invalid")
    void apiKeyFilter_shouldReturn401_withInvalidKey() throws Exception {
        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .header("X-Api-Key", "opck_invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 when API key is revoked")
    void apiKeyFilter_shouldReturn401_withRevokedKey() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "revoke filter key"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String rawKey = com.jayway.jsonpath.JsonPath.read(body, "$.key");
        String keyId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete(ApiPaths.apiKey(UUID.fromString(keyId)))
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .header("X-Api-Key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 when API key user no longer exists")
    void apiKeyFilter_shouldReturn401_whenUserNotFound() throws Exception {
        String rawKey = "opck_orphan_key_no_user_exists";
        UUID orphanUserId = UUID.randomUUID(); // not in users table

        // Bypass FK constraint to simulate a key whose user was deleted outside cascade
        dsl.execute("SET session_replication_role = replica");
        apiKeyRepository.save(ApiKeyModel.builder()
                .userId(orphanUserId)
                .name("orphan key")
                .keyHash(ApiKeyService.sha256(rawKey))
                .keyPrefix("opck_orpha")
                .build());
        dsl.execute("SET session_replication_role = origin");

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .header("X-Api-Key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 when API key is expired")
    void apiKeyFilter_shouldReturn401_withExpiredKey() throws Exception {
        String expiredHash = ApiKeyService.sha256("opck_expired_test_key_for_filter");
        apiKeyRepository.save(ApiKeyModel.builder()
                .userId(userId)
                .name("expired filter key")
                .keyHash(expiredHash)
                .keyPrefix("opck_expir")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .header("X-Api-Key", "opck_expired_test_key_for_filter"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should update lastUsedAt when API key is used")
    void apiKeyFilter_shouldUpdateLastUsedAt_onSuccessfulAuth() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "last used key"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String rawKey = com.jayway.jsonpath.JsonPath.read(body, "$.key");
        String keyId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .header("X-Api-Key", rawKey))
                .andExpect(status().isOk());

        apiKeyRepository.findById(UUID.fromString(keyId)).ifPresent(key ->
                assertThat(key.getLastUsedAt()).isNotNull()
        );
    }

    @Test
    @DisplayName("Raw key stored as hash — raw key not in database")
    void createApiKey_rawKeyShouldNotBeStoredInDatabase() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.API_KEYS)
                        .with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "hash check"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String rawKey = com.jayway.jsonpath.JsonPath.read(body, "$.key");
        String keyId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        apiKeyRepository.findById(UUID.fromString(keyId)).ifPresent(key -> {
            assertThat(key.getKeyHash()).isNotEqualTo(rawKey);
            assertThat(key.getKeyHash()).hasSize(64);
        });
    }
}
