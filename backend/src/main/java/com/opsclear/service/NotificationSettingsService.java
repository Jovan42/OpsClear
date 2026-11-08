package com.opsclear.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.NotificationType;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user notification mutes (ADR-0051 §Muting), stored in {@code users.preferences} under
 * {@code notifications.muted} as an array of type names. Default: nothing muted. Other keys in
 * {@code preferences} (and under {@code notifications}) are preserved on write.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSettingsService {

    static final String NOTIFICATIONS_KEY = "notifications";
    static final String MUTED_KEY = "muted";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * The user's muted types. Unknown names in the stored list (e.g. a type removed later) are ignored. Used by the
     * settings endpoint and by fan-out (Backend 2) to skip muted types.
     */
    @Transactional(readOnly = true)
    public Set<NotificationType> getMutedTypes(UUID userId) {
        JsonNode muted = readPreferences(userId).path(NOTIFICATIONS_KEY).path(MUTED_KEY);
        Set<NotificationType> types = EnumSet.noneOf(NotificationType.class);
        if (muted.isArray()) {
            for (JsonNode name : muted) {
                parseType(name.asText()).ifPresent(types::add);
            }
        }
        return types;
    }

    /**
     * Replaces the user's muted list. An unknown type or {@code SUBSCRIPTION_PAST_DUE} is a 400. Duplicates are
     * collapsed. Returns the stored set.
     */
    @Transactional
    public Set<NotificationType> replaceMutedTypes(UUID userId, List<String> muted) {
        Set<NotificationType> types = EnumSet.noneOf(NotificationType.class);
        for (String name : muted) {
            types.add(requireMutableType(name));
        }

        ObjectNode preferences = readPreferences(userId);
        JsonNode existing = preferences.get(NOTIFICATIONS_KEY);
        ObjectNode notifications = existing instanceof ObjectNode node ? node : objectMapper.createObjectNode();
        ArrayNode mutedArray = notifications.putArray(MUTED_KEY);
        types.forEach(t -> mutedArray.add(t.name()));
        preferences.set(NOTIFICATIONS_KEY, notifications);
        userRepository.updatePreferences(userId, preferences.toString());

        log.info("Updated notification mutes for user {}: {}", userId, types);
        return types;
    }

    // Stored preferences are a JSON object by schema default; anything else is treated as empty.
    private ObjectNode readPreferences(UUID userId) {
        String json = userRepository.findPreferences(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));
        try {
            JsonNode node = objectMapper.readTree(json);
            return node instanceof ObjectNode object ? object : objectMapper.createObjectNode();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(ErrorMessages.Notification.PREFERENCES_UNREADABLE, e);
        }
    }

    private static Optional<NotificationType> parseType(String name) {
        return Arrays.stream(NotificationType.values())
                .filter(t -> t.name().equals(name))
                .findFirst();
    }

    // --- Guards ---

    private static NotificationType requireMutableType(String name) {
        NotificationType type = parseType(name)
                .orElseThrow(() -> new BadRequestException(
                        String.format(ErrorMessages.Notification.UNKNOWN_TYPE, name)));
        if (!type.isMutable()) {
            throw new BadRequestException(ErrorMessages.Notification.CANNOT_MUTE_SUBSCRIPTION_PAST_DUE);
        }
        return type;
    }
}
