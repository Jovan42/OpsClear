package com.opsclear.service;

import com.opsclear.dto.CreateApiKeyRequest;
import com.opsclear.dto.CreateApiKeyResponse;
import com.opsclear.dto.ApiKeyResponse;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ApiKeyModel;
import com.opsclear.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private static final String KEY_PREFIX = "opck_";
    private static final int RANDOM_BYTES = 24;
    private static final int DISPLAY_PREFIX_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    @Transactional
    public CreateApiKeyResponse create(UUID userId, CreateApiKeyRequest request) {
        String rawKey = generateRawKey();
        String keyHash = sha256(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(DISPLAY_PREFIX_LENGTH, rawKey.length()));

        ApiKeyModel model = ApiKeyModel.builder()
                .userId(userId)
                .name(request.getName().strip())
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .expiresAt(request.getExpiresAt())
                .build();

        ApiKeyModel saved = apiKeyRepository.save(model);
        log.info("Created API key '{}' for user {}", saved.getName(), userId);
        return CreateApiKeyResponse.from(saved, rawKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(UUID userId) {
        return apiKeyRepository.findActiveByUserId(userId)
                .stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID keyId) {
        ApiKeyModel key = apiKeyRepository.findById(keyId)
                .filter(k -> k.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.ApiKey.NOT_FOUND));
        if (key.isRevoked()) {
            throw new NotFoundException(ErrorMessages.ApiKey.NOT_FOUND);
        }
        apiKeyRepository.revoke(keyId);
        log.info("Revoked API key {} for user {}", keyId, userId);
    }

    // --- Helpers ---

    private String generateRawKey() {
        byte[] bytes = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // @SneakyThrows avoids a try/catch block for NoSuchAlgorithmException.
    // SHA-256 is guaranteed by the Java spec so that branch is unreachable,
    // but JaCoCo would count an explicit catch as an uncovered line.
    @lombok.SneakyThrows
    public static String sha256(String input) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
