package com.opsclear.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Per-user UI preferences (ADR-0061), stored opaquely in {@code users.preferences} under {@code ui}. The frontend
 * owns the schema and defaults; the backend only enforces key, type and size limits. Every other key in
 * {@code preferences} (notably {@code notifications}, see {@link NotificationSettingsService}) is preserved on write.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesService {

    static final String UI_KEY = "ui";
    static final int MAX_KEYS = 100;
    static final int MAX_STRING_LENGTH = 256;
    static final int MAX_UI_BYTES = 16 * 1024;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** The user's stored {@code ui} object, or an empty map if nothing, or anything but an object, is stored. */
    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID userId) {
        return toMap(readUi(readPreferences(userRepository.findPreferences(userId))));
    }

    /**
     * Merges {@code patch} into the stored {@code ui} under a row lock: each top-level key is set, a {@code null}
     * value removes it. Validates the patch and the merged result (400 on any violation, nothing saved). Returns the
     * full stored {@code ui}.
     */
    @Transactional
    public Map<String, Object> update(UUID userId, Map<String, Object> patch) {
        ObjectNode preferences = readPreferences(userRepository.findPreferencesForUpdate(userId));
        requireValidPatch(patch);

        ObjectNode ui = readUi(preferences);
        patch.forEach((key, value) -> {
            if (value == null) {
                ui.remove(key);
            } else {
                ui.set(key, objectMapper.valueToTree(value));
            }
        });
        requireWithinLimits(ui);

        preferences.set(UI_KEY, ui);
        userRepository.updatePreferences(userId, preferences.toString());

        log.info("Updated UI preferences for user {}: keys {}", userId, patch.keySet());
        return toMap(ui);
    }

    // Stored preferences are a JSON object by schema default; anything else is treated as empty. The column is
    // JSONB, so it always holds valid JSON: @SneakyThrows avoids an unreachable catch for JsonProcessingException.
    @lombok.SneakyThrows
    private ObjectNode readPreferences(Optional<String> stored) {
        String json = stored.orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));
        JsonNode node = objectMapper.readTree(json);
        return node instanceof ObjectNode object ? object : objectMapper.createObjectNode();
    }

    private ObjectNode readUi(ObjectNode preferences) {
        JsonNode ui = preferences.get(UI_KEY);
        return ui instanceof ObjectNode object ? object : objectMapper.createObjectNode();
    }

    private Map<String, Object> toMap(ObjectNode ui) {
        return objectMapper.convertValue(ui, MAP_TYPE);
    }

    // --- Guards ---

    private static void requireValidPatch(Map<String, Object> patch) {
        if (patch == null) {
            throw new BadRequestException(ErrorMessages.UserPreferences.UI_REQUIRED);
        }
        patch.forEach((key, value) -> {
            requireValidKey(key);
            requireValidValue(key, value);
        });
    }

    private static void requireValidKey(String key) {
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new BadRequestException(String.format(ErrorMessages.UserPreferences.INVALID_KEY, key));
        }
    }

    // Jackson binds JSON scalars to String, Number or Boolean; objects and arrays (Map, List) are rejected.
    private static void requireValidValue(String key, Object value) {
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) {
                throw new BadRequestException(
                        String.format(ErrorMessages.UserPreferences.STRING_TOO_LONG, key, MAX_STRING_LENGTH));
            }
        } else if (value != null && !(value instanceof Number) && !(value instanceof Boolean)) {
            throw new BadRequestException(String.format(ErrorMessages.UserPreferences.INVALID_VALUE, key));
        }
    }

    private static void requireWithinLimits(ObjectNode ui) {
        if (ui.size() > MAX_KEYS) {
            throw new BadRequestException(String.format(ErrorMessages.UserPreferences.TOO_MANY_KEYS, MAX_KEYS));
        }
        if (ui.toString().getBytes(StandardCharsets.UTF_8).length > MAX_UI_BYTES) {
            throw new BadRequestException(String.format(ErrorMessages.UserPreferences.TOO_LARGE, MAX_UI_BYTES));
        }
    }
}
