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
import com.opsclear.service.UserPreferencesService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-synced UI preferences (ADR-0061), stored in {@code users.preferences} under {@code ui}, next to the
 * {@code notifications} key written by the notification settings API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("User preferences API")
class UserPreferencesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferencesService userPreferencesService;
    @Autowired private NotificationSettingsService notificationSettingsService;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID userId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        cleanUp();
        userId = UUID.randomUUID();
        otherId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(userId).email("prefs@example.com").name("Prefs").build());
        userRepository.save(UserModel.builder().id(otherId).email("other@example.com").name("Other").build());
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private RequestPostProcessor as(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    private String body(Map<String, Object> ui) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("ui", ui);
        return objectMapper.writeValueAsString(request);
    }

    private JsonNode storedPreferences(UUID id) throws Exception {
        return objectMapper.readTree(userRepository.findPreferences(id).orElseThrow());
    }

    private ResultActions patchAs(UUID id, String json) throws Exception {
        return mockMvc.perform(patch(ApiPaths.USER_PREFERENCES).with(as(id))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void assertUnchanged(String storedJson) throws Exception {
        assertThat(storedPreferences(userId)).isEqualTo(objectMapper.readTree(storedJson));
    }

    // --- GET ---

    @Test
    @DisplayName("GET returns an empty ui for a new user")
    void get_shouldReturnEmpty_forNewUser() throws Exception {
        mockMvc.perform(get(ApiPaths.USER_PREFERENCES).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui", anEmptyMap()));
    }

    @Test
    @DisplayName("GET treats non-object preferences and a non-object ui as empty")
    void get_shouldTreatOddStoredValuesAsEmpty() throws Exception {
        userRepository.updatePreferences(userId, "[1, 2]");
        userRepository.updatePreferences(otherId, "{\"ui\":\"broken\"}");

        mockMvc.perform(get(ApiPaths.USER_PREFERENCES).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui", anEmptyMap()));
        mockMvc.perform(get(ApiPaths.USER_PREFERENCES).with(as(otherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui", anEmptyMap()));
    }

    // --- PATCH ---

    @Test
    @DisplayName("PATCH then GET round-trips string, number and boolean values; other users are untouched")
    void patch_shouldRoundTrip() throws Exception {
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("theme", "dark");
        ui.put("dueSoonDays", 5);
        ui.put("showOverviewApprovals", false);

        patchAs(userId, body(ui))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui", aMapWithSize(3)))
                .andExpect(jsonPath("$.ui.theme").value("dark"))
                .andExpect(jsonPath("$.ui.dueSoonDays").value(5))
                .andExpect(jsonPath("$.ui.showOverviewApprovals").value(false));

        mockMvc.perform(get(ApiPaths.USER_PREFERENCES).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui", aMapWithSize(3)))
                .andExpect(jsonPath("$.ui.theme").value("dark"))
                .andExpect(jsonPath("$.ui.dueSoonDays").value(5))
                .andExpect(jsonPath("$.ui.showOverviewApprovals").value(false));

        JsonNode stored = storedPreferences(userId).get("ui");
        assertThat(stored.get("theme").asText()).isEqualTo("dark");
        assertThat(stored.get("dueSoonDays").asInt()).isEqualTo(5);
        assertThat(stored.get("showOverviewApprovals").isBoolean()).isTrue();

        mockMvc.perform(get(ApiPaths.USER_PREFERENCES).with(as(otherId)))
                .andExpect(jsonPath("$.ui", anEmptyMap()));
    }

    @Test
    @DisplayName("PATCH merges into the stored ui and null removes a key")
    void patch_shouldMerge_andRemoveOnNull() throws Exception {
        userRepository.updatePreferences(userId, "{\"ui\":{\"theme\":\"dark\",\"rowDensity\":\"COMPACT\"}}");

        patchAs(userId, "{\"ui\":{\"dueSoonDays\":5,\"rowDensity\":null}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui", aMapWithSize(2)))
                .andExpect(jsonPath("$.ui.theme").value("dark"))
                .andExpect(jsonPath("$.ui.dueSoonDays").value(5))
                .andExpect(jsonPath("$.ui.rowDensity").doesNotExist());

        JsonNode stored = storedPreferences(userId).get("ui");
        assertThat(stored.has("rowDensity")).isFalse();
        assertThat(stored.get("theme").asText()).isEqualTo("dark");
        assertThat(stored.get("dueSoonDays").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("PATCH stores a proper object when stored preferences aren't an object")
    void patch_shouldReplaceNonObjectPreferences() throws Exception {
        userRepository.updatePreferences(userId, "\"text\"");

        patchAs(userId, "{\"ui\":{\"theme\":\"light\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui.theme").value("light"));

        assertThat(storedPreferences(userId).at("/ui/theme").asText()).isEqualTo("light");
    }

    @Test
    @DisplayName("PATCH with a nested object is 400 and changes nothing")
    void patch_shouldReject_nestedObject() throws Exception {
        String stored = "{\"ui\":{\"theme\":\"dark\"}}";
        userRepository.updatePreferences(userId, stored);

        patchAs(userId, "{\"ui\":{\"dueSoonDays\":5,\"nested\":{\"a\":1}}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(String.format(ErrorMessages.UserPreferences.INVALID_VALUE, "nested")));
        assertUnchanged(stored);
    }

    @Test
    @DisplayName("PATCH with an array value is 400")
    void patch_shouldReject_array() throws Exception {
        patchAs(userId, "{\"ui\":{\"list\":[1,2]}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(String.format(ErrorMessages.UserPreferences.INVALID_VALUE, "list")));
        assertUnchanged("{}");
    }

    @Test
    @DisplayName("PATCH with a string over 256 characters is 400")
    void patch_shouldReject_oversizedString() throws Exception {
        patchAs(userId, body(Map.of("note", "x".repeat(257))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(String.format(ErrorMessages.UserPreferences.STRING_TOO_LONG, "note", 256)));
        assertUnchanged("{}");
    }

    @Test
    @DisplayName("PATCH with an invalid key is 400")
    void patch_shouldReject_invalidKey() throws Exception {
        patchAs(userId, body(Map.of("bad-key", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(String.format(ErrorMessages.UserPreferences.INVALID_KEY, "bad-key")));
        assertUnchanged("{}");
    }

    @Test
    @DisplayName("PATCH that would leave more than 100 keys is 400 and changes nothing")
    void patch_shouldReject_tooManyKeys() throws Exception {
        Map<String, Object> existing = new LinkedHashMap<>();
        for (int i = 0; i < 99; i++) {
            existing.put("k" + i, i);
        }
        String stored = objectMapper.writeValueAsString(Map.of("ui", existing));
        userRepository.updatePreferences(userId, stored);

        patchAs(userId, body(Map.of("k99", 1, "k100", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(String.format(ErrorMessages.UserPreferences.TOO_MANY_KEYS, 100)));
        assertUnchanged(stored);
    }

    @Test
    @DisplayName("PATCH that makes the ui larger than 16 KB is 400")
    void patch_shouldReject_overSizeLimit() throws Exception {
        Map<String, Object> ui = new LinkedHashMap<>();
        for (int i = 0; i < 80; i++) {
            ui.put("key" + i, "x".repeat(250));
        }

        patchAs(userId, body(ui))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(String.format(ErrorMessages.UserPreferences.TOO_LARGE, 16384)));
        assertUnchanged("{}");
    }

    @Test
    @DisplayName("PATCH without ui, or with null ui, is 400")
    void patch_shouldReject_missingUi() throws Exception {
        patchAs(userId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.UserPreferences.UI_REQUIRED));
        patchAs(userId, "{\"ui\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.UserPreferences.UI_REQUIRED));
        assertUnchanged("{}");
    }

    @Test
    @DisplayName("PATCH with a ui that isn't an object is 400")
    void patch_shouldReject_nonObjectUi() throws Exception {
        patchAs(userId, "{\"ui\":\"dark\"}")
                .andExpect(status().isBadRequest());
        patchAs(userId, "{\"ui\":[1]}")
                .andExpect(status().isBadRequest());
        assertUnchanged("{}");
    }

    // --- Coexistence with notification settings ---

    @Test
    @DisplayName("PATCH after a notification-settings PUT keeps the mutes")
    void patch_shouldKeepNotificationMutes() throws Exception {
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NotificationSettingsRequest(
                                List.of("NOTE_ADDED")))))
                .andExpect(status().isOk());

        patchAs(userId, body(Map.of("theme", "dark")))
                .andExpect(status().isOk());

        JsonNode prefs = storedPreferences(userId);
        assertThat(prefs.at("/notifications/muted/0").asText()).isEqualTo("NOTE_ADDED");
        assertThat(prefs.at("/ui/theme").asText()).isEqualTo("dark");
        mockMvc.perform(get(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId)))
                .andExpect(jsonPath("$.muted[0]").value("NOTE_ADDED"));
    }

    @Test
    @DisplayName("A notification-settings PUT after a PATCH keeps ui")
    void notificationSettingsPut_shouldKeepUi() throws Exception {
        patchAs(userId, body(Map.of("theme", "dark", "dueSoonDays", 7)))
                .andExpect(status().isOk());

        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NotificationSettingsRequest(
                                List.of("JOB_UPDATED")))))
                .andExpect(status().isOk());

        mockMvc.perform(get(ApiPaths.USER_PREFERENCES).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ui", aMapWithSize(2)))
                .andExpect(jsonPath("$.ui.theme").value("dark"))
                .andExpect(jsonPath("$.ui.dueSoonDays").value(7));
        assertThat(storedPreferences(userId).at("/notifications/muted/0").asText()).isEqualTo("JOB_UPDATED");
    }

    @Test
    @DisplayName("A preferences update waits for a held row lock, so a concurrent notification write isn't lost")
    void update_shouldWaitForRowLock() throws Exception {
        CompletableFuture<Map<String, Object>> update = transactionTemplate.execute(tx -> {
            userRepository.findPreferencesForUpdate(userId);
            CompletableFuture<Map<String, Object>> pending = CompletableFuture.supplyAsync(
                    () -> userPreferencesService.update(userId, Map.of("theme", "dark")));
            assertThatThrownBy(() -> pending.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            userRepository.updatePreferences(userId, "{\"notifications\":{\"muted\":[\"NOTE_ADDED\"]}}");
            return pending;
        });

        assertThat(update.get(10, TimeUnit.SECONDS)).containsEntry("theme", "dark");
        JsonNode prefs = storedPreferences(userId);
        assertThat(prefs.at("/notifications/muted/0").asText()).isEqualTo("NOTE_ADDED");
        assertThat(prefs.at("/ui/theme").asText()).isEqualTo("dark");
    }

    @Test
    @DisplayName("A notification-settings write waits for a held row lock, so a concurrent ui write isn't lost")
    void replaceMutedTypes_shouldWaitForRowLock() throws Exception {
        CompletableFuture<?> replace = transactionTemplate.execute(tx -> {
            userRepository.findPreferencesForUpdate(userId);
            CompletableFuture<?> pending = CompletableFuture.supplyAsync(
                    () -> notificationSettingsService.replaceMutedTypes(userId, List.of("NOTE_ADDED")));
            assertThatThrownBy(() -> pending.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            userRepository.updatePreferences(userId, "{\"ui\":{\"theme\":\"dark\"}}");
            return pending;
        });

        replace.get(10, TimeUnit.SECONDS);
        JsonNode prefs = storedPreferences(userId);
        assertThat(prefs.at("/ui/theme").asText()).isEqualTo("dark");
        assertThat(notificationSettingsService.getMutedTypes(userId)).containsExactly(NotificationType.NOTE_ADDED);
    }

    @Test
    @DisplayName("get and update throw NotFoundException for an unknown user ID")
    void service_shouldThrowNotFound_forUnknownUser() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> userPreferencesService.get(unknown))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.User.NOT_FOUND);
        assertThatThrownBy(() -> userPreferencesService.update(unknown, Map.of("theme", "dark")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.User.NOT_FOUND);
    }

    @Test
    @DisplayName("GET and PATCH without authentication are 401")
    void shouldRequireAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.USER_PREFERENCES))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(ApiPaths.USER_PREFERENCES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("theme", "dark"))))
                .andExpect(status().isUnauthorized());
        assertUnchanged("{}");
    }
}
