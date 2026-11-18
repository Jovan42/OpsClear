package com.opsclear.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPreferencesService")
class UserPreferencesServiceTest {

    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserPreferencesService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new UserPreferencesService(userRepository, objectMapper);
        userId = UUID.randomUUID();
    }

    private void storedForUpdate(String json) {
        when(userRepository.findPreferencesForUpdate(userId)).thenReturn(Optional.of(json));
    }

    private JsonNode savedPreferences() throws Exception {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePreferences(eq(userId), json.capture());
        return objectMapper.readTree(json.getValue());
    }

    private void assertRejected(Map<String, Object> patch, String message) {
        assertThatThrownBy(() -> service.update(userId, patch))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
        verify(userRepository, never()).updatePreferences(any(), any());
    }

    /** {@code count} keys {@code k000..}, each with a short string value. */
    private static Map<String, Object> keys(int from, int count) {
        Map<String, Object> ui = new LinkedHashMap<>();
        for (int i = from; i < from + count; i++) {
            ui.put(String.format("k%03d", i), "v");
        }
        return ui;
    }

    /** A {@code ui} map whose compact JSON is exactly {@code bytes} long, with fewer than 100 keys. */
    private Map<String, Object> uiOfSize(int bytes) throws Exception {
        Map<String, Object> ui = new LinkedHashMap<>();
        int size = 2; // {}
        for (int i = 0; ; i++) {
            String key = String.format("p%02d", i);
            int overhead = key.length() + 5 + (ui.isEmpty() ? 0 : 1); // "key":"" plus a comma after the first
            int remaining = bytes - size - overhead;
            if (remaining <= UserPreferencesService.MAX_STRING_LENGTH) {
                ui.put(key, "x".repeat(remaining));
                break;
            }
            ui.put(key, "x".repeat(200));
            size += overhead + 200;
        }
        assertThat(objectMapper.writeValueAsString(ui).getBytes(StandardCharsets.UTF_8)).hasSize(bytes);
        return ui;
    }

    // --- get ---

    @Test
    @DisplayName("get returns an empty map for default (empty) preferences")
    void get_shouldBeEmpty_byDefault() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("{}"));

        assertThat(service.get(userId)).isEmpty();
    }

    @Test
    @DisplayName("get returns the stored ui object and nothing from other keys")
    void get_shouldReturnStoredUi() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of(
                "{\"notifications\":{\"muted\":[\"NOTE_ADDED\"]},\"ui\":{\"theme\":\"dark\",\"dueSoonDays\":3,"
                        + "\"showOverviewApprovals\":false}}"));

        assertThat(service.get(userId)).containsExactly(
                Map.entry("theme", "dark"),
                Map.entry("dueSoonDays", 3),
                Map.entry("showOverviewApprovals", false));
    }

    @Test
    @DisplayName("get treats a non-object ui value as empty")
    void get_shouldBeEmpty_whenUiNotObject() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("{\"ui\":[1,2]}"));

        assertThat(service.get(userId)).isEmpty();
    }

    @Test
    @DisplayName("get treats non-object preferences as empty")
    void get_shouldBeEmpty_whenPreferencesNotObject() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("\"text\""));

        assertThat(service.get(userId)).isEmpty();
    }

    @Test
    @DisplayName("get throws 404 for an unknown user")
    void get_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.User.NOT_FOUND);
    }

    @Test
    @DisplayName("get propagates the parse error when stored preferences aren't JSON")
    void get_shouldThrow_whenPreferencesUnreadable() {
        when(userRepository.findPreferences(userId)).thenReturn(Optional.of("not json"));

        assertThatThrownBy(() -> service.get(userId)).isInstanceOf(JsonProcessingException.class);
    }

    // --- update: merge ---

    @Test
    @DisplayName("update merges keys into ui, keeps notifications and other keys, and returns the full ui")
    void update_shouldMerge_andKeepOtherKeys() throws Exception {
        storedForUpdate("{\"notifications\":{\"muted\":[\"NOTE_ADDED\"]},\"other\":1,"
                + "\"ui\":{\"theme\":\"dark\",\"dueSoonDays\":3}}");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("dueSoonDays", 5);
        patch.put("rowDensity", "COMPACT");
        patch.put("showOverviewApprovals", false);
        patch.put("ratio", 1.5);

        Map<String, Object> result = service.update(userId, patch);

        assertThat(result).containsExactly(
                Map.entry("theme", "dark"),
                Map.entry("dueSoonDays", 5),
                Map.entry("rowDensity", "COMPACT"),
                Map.entry("showOverviewApprovals", false),
                Map.entry("ratio", 1.5));
        JsonNode saved = savedPreferences();
        assertThat(saved.at("/notifications/muted/0").asText()).isEqualTo("NOTE_ADDED");
        assertThat(saved.get("other").asInt()).isEqualTo(1);
        assertThat(saved.at("/ui/theme").asText()).isEqualTo("dark");
        assertThat(saved.at("/ui/dueSoonDays").asInt()).isEqualTo(5);
        assertThat(saved.at("/ui/rowDensity").asText()).isEqualTo("COMPACT");
        assertThat(saved.at("/ui/showOverviewApprovals").isBoolean()).isTrue();
        assertThat(saved.at("/ui/ratio").asDouble()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("update removes a key whose value is null, and ignores null for a key that isn't stored")
    void update_shouldRemoveKey_whenNull() throws Exception {
        storedForUpdate("{\"ui\":{\"theme\":\"dark\",\"rowDensity\":\"COMPACT\"}}");
        Map<String, Object> patch = new HashMap<>();
        patch.put("rowDensity", null);
        patch.put("neverSet", null);

        Map<String, Object> result = service.update(userId, patch);

        assertThat(result).containsExactly(Map.entry("theme", "dark"));
        JsonNode ui = savedPreferences().get("ui");
        assertThat(ui.has("rowDensity")).isFalse();
        assertThat(ui.has("neverSet")).isFalse();
        assertThat(ui.get("theme").asText()).isEqualTo("dark");
    }

    @Test
    @DisplayName("update with an empty patch saves and returns the stored ui unchanged")
    void update_shouldKeepUi_whenPatchEmpty() throws Exception {
        storedForUpdate("{\"ui\":{\"theme\":\"dark\"}}");

        assertThat(service.update(userId, Map.of())).containsExactly(Map.entry("theme", "dark"));
        assertThat(savedPreferences().at("/ui/theme").asText()).isEqualTo("dark");
    }

    @Test
    @DisplayName("update treats non-object preferences as empty and stores a proper object")
    void update_shouldTreatNonObjectPreferencesAsEmpty() throws Exception {
        storedForUpdate("[1,2]");

        assertThat(service.update(userId, Map.of("theme", "light"))).containsExactly(Map.entry("theme", "light"));
        JsonNode saved = savedPreferences();
        assertThat(saved.isObject()).isTrue();
        assertThat(saved.at("/ui/theme").asText()).isEqualTo("light");
    }

    @Test
    @DisplayName("update replaces a non-object ui value with an object")
    void update_shouldReplaceNonObjectUi() throws Exception {
        storedForUpdate("{\"ui\":\"broken\",\"notifications\":{\"muted\":[]}}");

        assertThat(service.update(userId, Map.of("theme", "light"))).containsExactly(Map.entry("theme", "light"));
        JsonNode saved = savedPreferences();
        assertThat(saved.at("/ui/theme").asText()).isEqualTo("light");
        assertThat(saved.at("/notifications/muted").isArray()).isTrue();
    }

    @Test
    @DisplayName("update reads under the row lock, never with the plain read")
    void update_shouldUseLockingRead() {
        storedForUpdate("{}");

        service.update(userId, Map.of("theme", "light"));

        verify(userRepository).findPreferencesForUpdate(userId);
        verify(userRepository, never()).findPreferences(any());
    }

    @Test
    @DisplayName("update throws 404 for an unknown user and saves nothing")
    void update_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.findPreferencesForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, Map.of("theme", "light")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.User.NOT_FOUND);
        verify(userRepository, never()).updatePreferences(any(), any());
    }

    // --- update: validation ---

    @Test
    @DisplayName("update rejects a missing ui with 400")
    void update_shouldReject_whenUiMissing() {
        storedForUpdate("{}");

        assertRejected(null, ErrorMessages.UserPreferences.UI_REQUIRED);
    }

    @ParameterizedTest(name = "key \"{0}\"")
    @ValueSource(strings = {"", "with-dash", "with space", "dotted.key", "ključ", "a\n"})
    @DisplayName("update rejects a key that isn't 1-64 letters, digits or underscores")
    void update_shouldReject_invalidKey(String key) {
        storedForUpdate("{}");

        assertRejected(Map.of(key, "v"), String.format(ErrorMessages.UserPreferences.INVALID_KEY, key));
    }

    @Test
    @DisplayName("update rejects a 65-character key")
    void update_shouldReject_tooLongKey() {
        storedForUpdate("{}");
        String key = "x".repeat(65);

        assertRejected(Map.of(key, "v"), String.format(ErrorMessages.UserPreferences.INVALID_KEY, key));
    }

    @Test
    @DisplayName("update rejects an invalid key even when its value is null")
    void update_shouldReject_invalidKey_whenValueNull() {
        storedForUpdate("{}");
        Map<String, Object> patch = new HashMap<>();
        patch.put("bad-key", null);

        assertRejected(patch, String.format(ErrorMessages.UserPreferences.INVALID_KEY, "bad-key"));
    }

    @Test
    @DisplayName("update accepts a 64-character key of letters, digits and underscores")
    void update_shouldAccept_longestKey() {
        storedForUpdate("{}");
        String key = "Ab_9" + "x".repeat(60);

        assertThat(service.update(userId, Map.of(key, 1))).containsKey(key);
    }

    @Test
    @DisplayName("update rejects a nested object value")
    void update_shouldReject_nestedObject() {
        storedForUpdate("{}");

        assertRejected(Map.of("nested", Map.of("a", 1)),
                String.format(ErrorMessages.UserPreferences.INVALID_VALUE, "nested"));
    }

    @Test
    @DisplayName("update rejects an array value")
    void update_shouldReject_array() {
        storedForUpdate("{}");

        assertRejected(Map.of("list", List.of(1, 2)),
                String.format(ErrorMessages.UserPreferences.INVALID_VALUE, "list"));
    }

    @Test
    @DisplayName("update accepts string, integer, decimal and boolean values")
    void update_shouldAccept_scalarValues() {
        storedForUpdate("{}");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("s", "text");
        patch.put("i", 7);
        patch.put("d", 2.5);
        patch.put("b", true);

        assertThat(service.update(userId, patch)).containsExactly(
                Map.entry("s", "text"), Map.entry("i", 7), Map.entry("d", 2.5), Map.entry("b", true));
    }

    @Test
    @DisplayName("update accepts a 256-character string and rejects 257 characters")
    void update_shouldEnforceStringLength() {
        storedForUpdate("{}");
        String longest = "x".repeat(UserPreferencesService.MAX_STRING_LENGTH);

        assertThat(service.update(userId, Map.of("s", longest))).containsEntry("s", longest);
        assertThatThrownBy(() -> service.update(userId, Map.of("s", longest + "x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(String.format(ErrorMessages.UserPreferences.STRING_TOO_LONG, "s",
                        UserPreferencesService.MAX_STRING_LENGTH));
    }

    @Test
    @DisplayName("update allows exactly 100 keys after the merge")
    void update_shouldAccept_hundredKeys() throws Exception {
        storedForUpdate(objectMapper.writeValueAsString(Map.of("ui", keys(0, 98))));

        assertThat(service.update(userId, keys(98, 2))).hasSize(UserPreferencesService.MAX_KEYS);
    }

    @Test
    @DisplayName("update rejects more than 100 keys after the merge")
    void update_shouldReject_moreThanHundredKeys() throws Exception {
        storedForUpdate(objectMapper.writeValueAsString(Map.of("ui", keys(0, 99))));

        assertRejected(keys(99, 2),
                String.format(ErrorMessages.UserPreferences.TOO_MANY_KEYS, UserPreferencesService.MAX_KEYS));
    }

    @Test
    @DisplayName("update counts keys after removals, so a full ui can swap one key for another")
    void update_shouldCountKeysAfterRemovals() throws Exception {
        storedForUpdate(objectMapper.writeValueAsString(Map.of("ui", keys(0, 100))));
        Map<String, Object> patch = new HashMap<>();
        patch.put("k000", null);
        patch.put("k100", "v");

        Map<String, Object> result = service.update(userId, patch);

        assertThat(result).hasSize(UserPreferencesService.MAX_KEYS).containsKey("k100").doesNotContainKey("k000");
    }

    @Test
    @DisplayName("update accepts a ui of exactly 16 KB")
    void update_shouldAccept_exactlyMaxSize() throws Exception {
        storedForUpdate("{}");

        assertThat(service.update(userId, uiOfSize(UserPreferencesService.MAX_UI_BYTES))).isNotEmpty();
    }

    @Test
    @DisplayName("update rejects a ui over 16 KB")
    void update_shouldReject_overMaxSize() throws Exception {
        storedForUpdate("{}");

        assertRejected(uiOfSize(UserPreferencesService.MAX_UI_BYTES + 1),
                String.format(ErrorMessages.UserPreferences.TOO_LARGE, UserPreferencesService.MAX_UI_BYTES));
    }

    @Test
    @DisplayName("update measures the size in UTF-8 bytes, not characters")
    void update_shouldMeasureSizeInBytes() {
        storedForUpdate("{}");
        Map<String, Object> patch = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            patch.put("p" + i, "č".repeat(200)); // 10,000 characters of values, 20,000 bytes
        }

        assertRejected(patch,
                String.format(ErrorMessages.UserPreferences.TOO_LARGE, UserPreferencesService.MAX_UI_BYTES));
    }

    @Test
    @DisplayName("update counts already stored keys towards the size limit")
    void update_shouldCountStoredKeysTowardsSize() throws Exception {
        Map<String, Object> stored = uiOfSize(UserPreferencesService.MAX_UI_BYTES);
        storedForUpdate(objectMapper.writeValueAsString(Map.of("ui", stored)));

        assertRejected(Map.of("extra", 1),
                String.format(ErrorMessages.UserPreferences.TOO_LARGE, UserPreferencesService.MAX_UI_BYTES));
    }
}
