package com.opsclear.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.dto.NotificationSettingsRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.NotificationType;
import com.opsclear.model.UserModel;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.NotificationSettingsService;
import org.junit.jupiter.api.AfterEach;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification mute settings (ADR-0051 §Muting), stored in {@code users.preferences} under
 * {@code notifications.muted}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Notification settings API")
class NotificationSettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationSettingsService notificationSettingsService;

    private UUID userId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        cleanUp();
        userId = UUID.randomUUID();
        otherId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(userId).email("mute@example.com").name("Mute Me").build());
        userRepository.save(UserModel.builder().id(otherId).email("other@example.com").name("Other").build());
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private RequestPostProcessor as(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    private String body(List<String> muted) throws Exception {
        return objectMapper.writeValueAsString(new NotificationSettingsRequest(muted));
    }

    private JsonNode storedPreferences(UUID id) throws Exception {
        return objectMapper.readTree(userRepository.findPreferences(id).orElseThrow());
    }

    @Test
    @DisplayName("GET returns nothing muted by default")
    void get_shouldReturnEmpty_byDefault() throws Exception {
        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", hasSize(0)));
    }

    @Test
    @DisplayName("PUT replaces the muted list and GET returns it; a second PUT replaces it again")
    void put_shouldReplaceList() throws Exception {
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of("NOTE_ADDED", "JOB_STATUS_CHANGED", "NOTE_ADDED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", contains("JOB_STATUS_CHANGED", "NOTE_ADDED")));

        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", contains("JOB_STATUS_CHANGED", "NOTE_ADDED")));
        assertThat(storedPreferences(userId).at("/notifications/muted")).hasSize(2);

        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of("JOB_UPDATED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", contains("JOB_UPDATED")));
        JsonNode muted = storedPreferences(userId).at("/notifications/muted");
        assertThat(muted).hasSize(1);
        assertThat(muted.get(0).asText()).isEqualTo("JOB_UPDATED");

        // Another user's settings are untouched.
        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS).with(as(otherId)))
                .andExpect(jsonPath("$.muted", hasSize(0)));
    }

    @Test
    @DisplayName("PUT with an empty list unmutes everything")
    void put_shouldClear_whenEmpty() throws Exception {
        userRepository.updatePreferences(userId, "{\"notifications\":{\"muted\":[\"JOB_UPDATED\"]}}");

        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", hasSize(0)));
        assertThat(storedPreferences(userId).at("/notifications/muted")).isEmpty();
    }

    @Test
    @DisplayName("PUT with SUBSCRIPTION_PAST_DUE is 400 and changes nothing")
    void put_shouldReject_pastDue() throws Exception {
        userRepository.updatePreferences(userId, "{\"notifications\":{\"muted\":[\"JOB_UPDATED\"]}}");

        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of("NOTE_ADDED", "SUBSCRIPTION_PAST_DUE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SUBSCRIPTION_PAST_DUE notifications can't be muted"));

        JsonNode muted = storedPreferences(userId).at("/notifications/muted");
        assertThat(muted).hasSize(1);
        assertThat(muted.get(0).asText()).isEqualTo("JOB_UPDATED");
    }

    @Test
    @DisplayName("PUT with an unknown type is 400 and changes nothing")
    void put_shouldReject_unknownType() throws Exception {
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of("NOT_A_TYPE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown notification type: NOT_A_TYPE"));
        assertThat(storedPreferences(userId).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("PUT without muted is 400")
    void put_shouldReject_missingMuted() throws Exception {
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("muted: muted is required"));
    }

    @Test
    @DisplayName("PUT keeps other preferences keys, including other keys under notifications")
    void put_shouldPreserveOtherPreferenceKeys() throws Exception {
        userRepository.updatePreferences(userId,
                "{\"theme\":\"dark\",\"dashboard\":{\"compact\":true},\"notifications\":{\"sound\":false}}");

        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of("JOB_DELETED"))))
                .andExpect(status().isOk());

        JsonNode prefs = storedPreferences(userId);
        assertThat(prefs.get("theme").asText()).isEqualTo("dark");
        assertThat(prefs.at("/dashboard/compact").asBoolean()).isTrue();
        assertThat(prefs.at("/notifications/sound").isBoolean()).isTrue();
        assertThat(prefs.at("/notifications/sound").asBoolean()).isFalse();
        assertThat(prefs.at("/notifications/muted/0").asText()).isEqualTo("JOB_DELETED");
    }

    @Test
    @DisplayName("GET ignores unknown stored type names and treats a non-array muted value as nothing muted")
    void get_shouldTolerateOddStoredValues() throws Exception {
        userRepository.updatePreferences(userId,
                "{\"notifications\":{\"muted\":[\"NOTE_ADDED\",\"REMOVED_TYPE\",\"JOB_UPDATED\"]}}");
        userRepository.updatePreferences(otherId, "{\"notifications\":{\"muted\":\"NOTE_ADDED\"}}");

        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", contains("JOB_UPDATED", "NOTE_ADDED")));
        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS).with(as(otherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", hasSize(0)));
    }

    @Test
    @DisplayName("GET treats non-object preferences as empty; PUT then stores a proper object")
    void shouldTreatNonObjectPreferencesAsEmpty() throws Exception {
        userRepository.updatePreferences(userId, "[1, 2]");

        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", hasSize(0)));
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of("NOTE_ADDED"))))
                .andExpect(status().isOk());

        assertThat(storedPreferences(userId).at("/notifications/muted/0").asText()).isEqualTo("NOTE_ADDED");
    }

    @Test
    @DisplayName("PUT replaces a non-object notifications value with an object holding muted")
    void put_shouldReplaceNonObjectNotificationsValue() throws Exception {
        userRepository.updatePreferences(userId, "{\"theme\":\"dark\",\"notifications\":\"off\"}");

        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of("JOB_ASSIGNED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", contains("JOB_ASSIGNED")));

        JsonNode prefs = storedPreferences(userId);
        assertThat(prefs.get("theme").asText()).isEqualTo("dark");
        assertThat(prefs.at("/notifications/muted/0").asText()).isEqualTo("JOB_ASSIGNED");
    }

    @Test
    @DisplayName("PUT accepts all 15 mutable types")
    void put_shouldAcceptAllMutableTypes() throws Exception {
        List<String> mutable = Arrays.stream(NotificationType.values())
                .filter(NotificationType::isMutable)
                .map(Enum::name)
                .toList();

        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mutable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted", hasSize(15)));
    }

    @Test
    @DisplayName("getMutedTypes (used by fan-out) throws NotFoundException for an unknown user ID")
    void getMutedTypes_shouldThrowNotFound_forUnknownUser() {
        assertThatThrownBy(() -> notificationSettingsService.getMutedTypes(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.User.NOT_FOUND);
    }

    @Test
    @DisplayName("GET and PUT without authentication are 401")
    void shouldRequireAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of())))
                .andExpect(status().isUnauthorized());
    }
}
