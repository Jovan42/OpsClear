package com.opsclear.service;

import com.opsclear.dto.ApiKeyResponse;
import com.opsclear.dto.CreateApiKeyRequest;
import com.opsclear.dto.CreateApiKeyResponse;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ApiKeyModel;
import com.opsclear.repository.ApiKeyRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;

    private ApiKeyService apiKeyService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository);
        userId = UUID.randomUUID();
    }

    // --- create ---

    @Test
    @DisplayName("create should generate a key with opck_ prefix and return raw key once")
    void create_shouldGenerateKeyWithPrefix_andReturnRawKeyOnce() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("deploy script");

        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.save(any())).thenAnswer(inv -> {
            ApiKeyModel m = inv.getArgument(0);
            m.setId(keyId);
            m.setCreatedAt(Instant.now());
            return m;
        });

        CreateApiKeyResponse response = apiKeyService.create(userId, request);

        assertThat(response.getKey()).startsWith("opck_");
        assertThat(response.getKeyPrefix()).startsWith("opck_");
        assertThat(response.getName()).isEqualTo("deploy script");
        assertThat(response.getId()).isEqualTo(keyId);
    }

    @Test
    @DisplayName("create should strip whitespace from name")
    void create_shouldStripNameWhitespace() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("  my key  ");

        when(apiKeyRepository.save(any())).thenAnswer(inv -> {
            ApiKeyModel m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        ArgumentCaptor<ApiKeyModel> captor = ArgumentCaptor.forClass(ApiKeyModel.class);
        apiKeyService.create(userId, request);
        verify(apiKeyRepository).save(captor.capture());

        assertThat(captor.getValue().getName()).isEqualTo("my key");
    }

    @Test
    @DisplayName("create should store SHA-256 hash, not raw key")
    void create_shouldStoreHashNotRawKey() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("test key");

        when(apiKeyRepository.save(any())).thenAnswer(inv -> {
            ApiKeyModel m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        ArgumentCaptor<ApiKeyModel> captor = ArgumentCaptor.forClass(ApiKeyModel.class);
        CreateApiKeyResponse response = apiKeyService.create(userId, request);
        verify(apiKeyRepository).save(captor.capture());

        String savedHash = captor.getValue().getKeyHash();
        String expectedHash = ApiKeyService.sha256(response.getKey());
        assertThat(savedHash).isEqualTo(expectedHash);
        assertThat(savedHash).doesNotContain("opck_");
    }

    // --- list ---

    @Test
    @DisplayName("list should return active keys for user")
    void list_shouldReturnActiveKeysForUser() {
        ApiKeyModel key1 = ApiKeyModel.builder()
                .id(UUID.randomUUID()).userId(userId).name("key1")
                .keyPrefix("opck_abc1").createdAt(Instant.now()).build();
        ApiKeyModel key2 = ApiKeyModel.builder()
                .id(UUID.randomUUID()).userId(userId).name("key2")
                .keyPrefix("opck_def2").createdAt(Instant.now()).build();

        when(apiKeyRepository.findActiveByUserId(userId)).thenReturn(List.of(key1, key2));

        List<ApiKeyResponse> result = apiKeyService.list(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("key1");
        assertThat(result.get(1).getName()).isEqualTo("key2");
    }

    // --- revoke ---

    @Test
    @DisplayName("revoke should call repository when key exists and belongs to user")
    void revoke_shouldRevokeKey_whenKeyExistsAndBelongsToUser() {
        UUID keyId = UUID.randomUUID();
        ApiKeyModel key = ApiKeyModel.builder()
                .id(keyId).userId(userId).name("deploy")
                .keyPrefix("opck_abc1").createdAt(Instant.now()).build();

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        apiKeyService.revoke(userId, keyId);

        verify(apiKeyRepository).revoke(keyId);
    }

    @Test
    @DisplayName("revoke should throw NotFoundException when key does not exist")
    void revoke_shouldThrowNotFoundException_whenKeyNotFound() {
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revoke(userId, keyId))
                .isInstanceOf(NotFoundException.class);

        verify(apiKeyRepository, never()).revoke(any());
    }

    @Test
    @DisplayName("revoke should throw NotFoundException when key belongs to different user")
    void revoke_shouldThrowNotFoundException_whenKeyBelongsToDifferentUser() {
        UUID keyId = UUID.randomUUID();
        ApiKeyModel key = ApiKeyModel.builder()
                .id(keyId).userId(UUID.randomUUID()).name("other user key")
                .keyPrefix("opck_abc1").createdAt(Instant.now()).build();

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.revoke(userId, keyId))
                .isInstanceOf(NotFoundException.class);

        verify(apiKeyRepository, never()).revoke(any());
    }

    @Test
    @DisplayName("revoke should throw NotFoundException when key is already revoked")
    void revoke_shouldThrowNotFoundException_whenKeyAlreadyRevoked() {
        UUID keyId = UUID.randomUUID();
        ApiKeyModel key = ApiKeyModel.builder()
                .id(keyId).userId(userId).name("old key")
                .keyPrefix("opck_abc1").createdAt(Instant.now())
                .revokedAt(Instant.now()).build();

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.revoke(userId, keyId))
                .isInstanceOf(NotFoundException.class);

        verify(apiKeyRepository, never()).revoke(any());
    }

    // --- sha256 ---

    @Test
    @DisplayName("sha256 should produce consistent 64-char hex output")
    void sha256_shouldProduceConsistentHexOutput() {
        String hash1 = ApiKeyService.sha256("opck_test123");
        String hash2 = ApiKeyService.sha256("opck_test123");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("sha256 should produce different hashes for different inputs")
    void sha256_shouldProduceDifferentHashes_forDifferentInputs() {
        String hash1 = ApiKeyService.sha256("opck_aaa");
        String hash2 = ApiKeyService.sha256("opck_bbb");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
