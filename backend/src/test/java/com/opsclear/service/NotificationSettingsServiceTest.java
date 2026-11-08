package com.opsclear.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.NotificationType;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSettingsService")
class NotificationSettingsServiceTest {

    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationSettingsService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new NotificationSettingsService(userRepository, objectMapper);
        userId = UUID.randomUUID();
    }

    private JsonNode savedPreferences() throws Exception {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePreferences(eq(userId), json.capture());
        return objectMapper.readTree(json.getValue());
    }

    // --- getMutedTypes ---

    @Test
    @DisplayName("getMutedTypes returns nothing muted for default (empty) preferences")
    void getMutedTypes_shouldBeEmpty_byDefault() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("{}"));

        assertThat(service.getMutedTypes(userId)).isEmpty();
    }

    @Test
    @DisplayName("getMutedTypes reads notifications.muted and ignores unknown type names")
    void getMutedTypes_shouldReadStoredList_ignoringUnknown() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of(
                "{\"notifications\":{\"muted\":[\"JOB_UPDATED\",\"NO_SUCH_TYPE\",\"NOTE_ADDED\"]}}"));

        assertThat(service.getMutedTypes(userId))
                .containsExactlyInAnyOrder(NotificationType.JOB_UPDATED, NotificationType.NOTE_ADDED);
    }

    @Test
    @DisplayName("getMutedTypes treats a non-array muted value as nothing muted")
    void getMutedTypes_shouldBeEmpty_whenMutedNotArray() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("{\"notifications\":{\"muted\":\"x\"}}"));

        assertThat(service.getMutedTypes(userId)).isEmpty();
    }

    @Test
    @DisplayName("getMutedTypes treats non-object preferences as empty")
    void getMutedTypes_shouldBeEmpty_whenPreferencesNotObject() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("[1,2]"));

        assertThat(service.getMutedTypes(userId)).isEmpty();
    }

    @Test
    @DisplayName("getMutedTypes throws 404 for an unknown user")
    void getMutedTypes_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMutedTypes(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.User.NOT_FOUND);
    }

    @Test
    @DisplayName("getMutedTypes fails with IllegalStateException when stored preferences aren't JSON")
    void getMutedTypes_shouldThrow_whenPreferencesUnreadable() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("not json"));

        assertThatThrownBy(() -> service.getMutedTypes(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ErrorMessages.Notification.PREFERENCES_UNREADABLE);
    }

    // --- replaceMutedTypes ---

    @Test
    @DisplayName("replaceMutedTypes writes notifications.muted and keeps other preference keys")
    void replaceMutedTypes_shouldWriteList_andPreserveOtherKeys() throws Exception {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of(
                "{\"theme\":\"dark\",\"notifications\":{\"muted\":[\"JOB_UPDATED\"],\"other\":true}}"));

        Set<NotificationType> result = service.replaceMutedTypes(userId,
                List.of("NOTE_ADDED", "JOB_STATUS_CHANGED", "NOTE_ADDED"));

        assertThat(result).containsExactly(NotificationType.JOB_STATUS_CHANGED, NotificationType.NOTE_ADDED);
        JsonNode saved = savedPreferences();
        assertThat(saved.get("theme").asText()).isEqualTo("dark");
        assertThat(saved.at("/notifications/other").asBoolean()).isTrue();
        assertThat(saved.at("/notifications/muted")).hasSize(2);
        assertThat(saved.at("/notifications/muted/0").asText()).isEqualTo("JOB_STATUS_CHANGED");
        assertThat(saved.at("/notifications/muted/1").asText()).isEqualTo("NOTE_ADDED");
    }

    @Test
    @DisplayName("replaceMutedTypes creates the notifications object when it's missing or not an object")
    void replaceMutedTypes_shouldCreateNotificationsObject() throws Exception {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("{\"notifications\":\"x\"}"));

        service.replaceMutedTypes(userId, List.of("JOB_DELETED"));

        assertThat(savedPreferences().at("/notifications/muted/0").asText()).isEqualTo("JOB_DELETED");
    }

    @Test
    @DisplayName("replaceMutedTypes with an empty list unmutes everything")
    void replaceMutedTypes_shouldClear_whenEmptyList() throws Exception {
        when(userRepository.findPreferences(userId))
                .thenReturn(Optional.of("{\"notifications\":{\"muted\":[\"JOB_UPDATED\"]}}"));

        assertThat(service.replaceMutedTypes(userId, List.of())).isEmpty();
        assertThat(savedPreferences().at("/notifications/muted")).isEmpty();
    }

    @Test
    @DisplayName("replaceMutedTypes accepts every type except SUBSCRIPTION_PAST_DUE")
    void replaceMutedTypes_shouldAcceptAllMutableTypes() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("{}"));
        List<String> mutable = Arrays.stream(NotificationType.values())
                .filter(NotificationType::isMutable)
                .map(Enum::name)
                .toList();

        assertThat(service.replaceMutedTypes(userId, mutable)).hasSize(15);
    }

    @Test
    @DisplayName("replaceMutedTypes rejects SUBSCRIPTION_PAST_DUE with 400 and saves nothing")
    void replaceMutedTypes_shouldThrow_whenPastDue() {
        assertThatThrownBy(() -> service.replaceMutedTypes(userId, List.of("NOTE_ADDED", "SUBSCRIPTION_PAST_DUE")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.Notification.CANNOT_MUTE_SUBSCRIPTION_PAST_DUE);
        verify(userRepository, never()).updatePreferences(any(), any());
    }

    @Test
    @DisplayName("replaceMutedTypes rejects an unknown type with 400 and saves nothing")
    void replaceMutedTypes_shouldThrow_whenUnknownType() {
        assertThatThrownBy(() -> service.replaceMutedTypes(userId, List.of("NOT_A_TYPE")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(String.format(ErrorMessages.Notification.UNKNOWN_TYPE, "NOT_A_TYPE"));
        verify(userRepository, never()).updatePreferences(any(), any());
    }

    @Test
    @DisplayName("replaceMutedTypes throws 404 for an unknown user")
    void replaceMutedTypes_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceMutedTypes(userId, List.of("NOTE_ADDED")))
                .isInstanceOf(NotFoundException.class);
        verify(userRepository, never()).updatePreferences(any(), any());
    }
}
