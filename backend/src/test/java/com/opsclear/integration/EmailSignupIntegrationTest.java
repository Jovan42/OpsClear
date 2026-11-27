package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.generated.jooq.tables.records.EmailSignupsRecord;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.EmailSignupRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.EMAIL_SIGNUPS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Early access sign-ups (ADR-0066, JOB-441) with the sign-up switch at its default, open. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Email sign-up endpoints")
class EmailSignupIntegrationTest {

    private static final String EARLY_ACCESS = "early-access";
    private static final String OTHER_LIST = "launch-webinar";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private EmailSignupRepository emailSignupRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID superUserId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // TRUNCATE ... CASCADE (UserRepository.deleteAll) clears whatever other classes left behind
        userRepository.deleteAll();
        emailSignupRepository.deleteAll();

        superUserId = UUID.randomUUID();
        userId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(superUserId).email("super@example.com").name("Super").build());
        userRepository.save(UserModel.builder().id(userId).email("user@example.com").name("User").build());
        dsl.update(USERS).set(USERS.SUPER_USER, true).where(USERS.ID.eq(superUserId)).execute();
    }

    // ─── POST /api/public/signups ─────────────────────────────────────────────

    @Test
    @DisplayName("signUp_shouldReturn204AndStoreRow_withListUpperCaseCodeLocaleAndUtm_withoutAuth")
    void signUp_shouldReturn204AndStoreRow_withListUpperCaseCodeLocaleAndUtm_withoutAuth() throws Exception {
        Map<String, Object> body = signup("  Ana@Example.com ");
        body.put("locale", "sr");
        body.put("utmSource", "linkedin");
        body.put("utmMedium", "social");
        body.put("utmCampaign", "launch");
        body.put("code", " trial ");
        body.put("website", "");

        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, body)).andExpect(status().isNoContent());

        EmailSignupsRecord row = dsl.selectFrom(EMAIL_SIGNUPS).fetchSingle();
        assertThat(row.getList()).isEqualTo(EARLY_ACCESS);
        assertThat(row.getEmail()).isEqualTo("Ana@Example.com");
        assertThat(row.getCode()).isEqualTo("TRIAL");
        assertThat(row.getLocale()).isEqualTo("sr");
        assertThat(row.getUtmSource()).isEqualTo("linkedin");
        assertThat(row.getUtmMedium()).isEqualTo("social");
        assertThat(row.getUtmCampaign()).isEqualTo("launch");
        assertThat(row.getConsentAt()).isNotNull();
        assertThat(row.getRedeemedAt()).isNull();
        assertThat(row.getRedeemedOrgId()).isNull();
    }

    @Test
    @DisplayName("signUp_shouldStoreRowWithoutCode_whenCodeIsMissingOrBlank")
    void signUp_shouldStoreRowWithoutCode_whenCodeIsMissingOrBlank() throws Exception {
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup("ana@example.com")))
                .andExpect(status().isNoContent());
        Map<String, Object> blankCode = signup("bob@example.com");
        blankCode.put("code", "  ");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, blankCode)).andExpect(status().isNoContent());

        assertThat(dsl.selectFrom(EMAIL_SIGNUPS).fetch(EMAIL_SIGNUPS.CODE)).hasSize(2).containsOnlyNulls();
        assertThat(dsl.selectFrom(EMAIL_SIGNUPS).fetch(EMAIL_SIGNUPS.LOCALE)).containsOnlyNulls();
    }

    @Test
    @DisplayName("signUp_shouldReturn204AndKeepOneRow_whenSameEmailInAnotherCase")
    void signUp_shouldReturn204AndKeepOneRow_whenSameEmailInAnotherCase() throws Exception {
        Map<String, Object> first = signup("ana@example.com");
        first.put("utmSource", "linkedin");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, first)).andExpect(status().isNoContent());
        Map<String, Object> second = signup("ANA@Example.COM");
        second.put("utmSource", "facebook");
        second.put("locale", "en");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, second)).andExpect(status().isNoContent());

        EmailSignupsRecord row = dsl.selectFrom(EMAIL_SIGNUPS).fetchSingle();
        assertThat(row.getEmail()).isEqualTo("ana@example.com");
        assertThat(row.getUtmSource()).isEqualTo("linkedin");
        assertThat(row.getLocale()).isNull();
    }

    @Test
    @DisplayName("signUp_shouldStoreTwoRows_whenSameEmailOnTwoLists")
    void signUp_shouldStoreTwoRows_whenSameEmailOnTwoLists() throws Exception {
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup("ana@example.com")))
                .andExpect(status().isNoContent());
        // Only early-access is an allowed list today, so the second list's row is inserted directly: this checks
        // that the unique index is per list.
        dsl.insertInto(EMAIL_SIGNUPS)
                .set(EMAIL_SIGNUPS.LIST, OTHER_LIST)
                .set(EMAIL_SIGNUPS.EMAIL, "ana@example.com")
                .set(EMAIL_SIGNUPS.CONSENT_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .execute();
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup("Ana@example.com")))
                .andExpect(status().isNoContent());

        assertThat(dsl.selectFrom(EMAIL_SIGNUPS).fetch(EMAIL_SIGNUPS.LIST))
                .containsExactlyInAnyOrder(EARLY_ACCESS, OTHER_LIST);
    }

    @Test
    @DisplayName("signUp_shouldFillAnEmptyCode_onDuplicate")
    void signUp_shouldFillAnEmptyCode_onDuplicate() throws Exception {
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup("ana@example.com")))
                .andExpect(status().isNoContent());
        Map<String, Object> withCode = signup("Ana@Example.com");
        withCode.put("code", "trial");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, withCode)).andExpect(status().isNoContent());

        EmailSignupsRecord row = dsl.selectFrom(EMAIL_SIGNUPS).fetchSingle();
        assertThat(row.getCode()).isEqualTo("TRIAL");
        assertThat(row.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    @DisplayName("signUp_shouldNeverOverwriteAnExistingCode_onDuplicate")
    void signUp_shouldNeverOverwriteAnExistingCode_onDuplicate() throws Exception {
        Map<String, Object> withCode = signup("ana@example.com");
        withCode.put("code", "TRIAL");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, withCode)).andExpect(status().isNoContent());
        // No other code exists yet, so the existing one is set to a value the map doesn't know: a duplicate with
        // TRIAL must leave it alone, and a duplicate without a code must not clear it.
        dsl.update(EMAIL_SIGNUPS).set(EMAIL_SIGNUPS.CODE, "EARLYBIRD").execute();
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, withCode)).andExpect(status().isNoContent());
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup("ana@example.com")))
                .andExpect(status().isNoContent());

        assertThat(dsl.selectFrom(EMAIL_SIGNUPS).fetchSingle().getCode()).isEqualTo("EARLYBIRD");
    }

    @Test
    @DisplayName("signUp_shouldReturn204AndStoreNothing_whenHoneypotIsFilled")
    void signUp_shouldReturn204AndStoreNothing_whenHoneypotIsFilled() throws Exception {
        Map<String, Object> body = signup("bot@example.com");
        body.put("website", "http://spam.example");

        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, body)).andExpect(status().isNoContent());

        assertThat(dsl.fetchCount(EMAIL_SIGNUPS)).isZero();
    }

    @Test
    @DisplayName("signUp_shouldReturn400_whenListIsUnknown")
    void signUp_shouldReturn400_whenListIsUnknown() throws Exception {
        Map<String, Object> body = signup("ana@example.com");
        body.put("list", "newsletter");

        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown sign-up list"));

        assertThat(dsl.fetchCount(EMAIL_SIGNUPS)).isZero();
    }

    @Test
    @DisplayName("signUp_shouldReturn400_whenListIsMissing")
    void signUp_shouldReturn400_whenListIsMissing() throws Exception {
        Map<String, Object> body = signup("ana@example.com");
        body.remove("list");

        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("list: List is required"));
    }

    @Test
    @DisplayName("signUp_shouldReturn400_whenCodeIsUnknown")
    void signUp_shouldReturn400_whenCodeIsUnknown() throws Exception {
        Map<String, Object> body = signup("ana@example.com");
        body.put("code", "FREE100");

        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("This code isn't valid"));

        assertThat(dsl.fetchCount(EMAIL_SIGNUPS)).isZero();
    }

    @Test
    @DisplayName("signUp_shouldReturn400_whenEmailIsInvalidMissingOrTooLong")
    void signUp_shouldReturn400_whenEmailIsInvalidMissingOrTooLong() throws Exception {
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email: Email must be a valid email address"));
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup("  ")))
                .andExpect(status().isBadRequest());
        String tooLong = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com";
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, signup(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email: Email must not exceed 254 characters"));

        assertThat(dsl.fetchCount(EMAIL_SIGNUPS)).isZero();
    }

    @Test
    @DisplayName("signUp_shouldReturn400_whenConsentIsFalseOrMissing")
    void signUp_shouldReturn400_whenConsentIsFalseOrMissing() throws Exception {
        Map<String, Object> declined = signup("ana@example.com");
        declined.put("consent", false);
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, declined))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("consent: Consent is required"));

        Map<String, Object> missing = signup("ana@example.com");
        missing.remove("consent");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, missing))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("consent: Consent is required"));

        assertThat(dsl.fetchCount(EMAIL_SIGNUPS)).isZero();
    }

    @Test
    @DisplayName("signUp_shouldReturn400_whenLocaleUtmOrCodeBreakTheirRules")
    void signUp_shouldReturn400_whenLocaleUtmOrCodeBreakTheirRules() throws Exception {
        Map<String, Object> badLocale = signup("ana@example.com");
        badLocale.put("locale", "de");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, badLocale))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("locale: Locale must be en or sr"));

        for (String utm : List.of("utmSource", "utmMedium", "utmCampaign")) {
            Map<String, Object> longUtm = signup("ana@example.com");
            longUtm.put(utm, "x".repeat(101));
            mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, longUtm))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(utm + ": " + utm + " must not exceed 100 characters"));
        }

        Map<String, Object> longCode = signup("ana@example.com");
        longCode.put("code", "T".repeat(31));
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, longCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("code: Code must not exceed 30 characters"));

        assertThat(dsl.fetchCount(EMAIL_SIGNUPS)).isZero();
    }

    @Test
    @DisplayName("signUp_shouldStore100CharacterUtmValues")
    void signUp_shouldStore100CharacterUtmValues() throws Exception {
        Map<String, Object> body = signup("ana@example.com");
        body.put("utmSource", "s".repeat(100));
        body.put("utmMedium", "m".repeat(100));
        body.put("utmCampaign", "c".repeat(100));

        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, body)).andExpect(status().isNoContent());

        EmailSignupsRecord row = dsl.selectFrom(EMAIL_SIGNUPS).fetchSingle();
        assertThat(row.getUtmSource()).isEqualTo("s".repeat(100));
        assertThat(row.getUtmMedium()).isEqualTo("m".repeat(100));
        assertThat(row.getUtmCampaign()).isEqualTo("c".repeat(100));
    }

    // ─── GET /api/public/signup ───────────────────────────────────────────────

    @Test
    @DisplayName("status_shouldReturnOpen_byDefault_withoutAuth")
    void status_shouldReturnOpen_byDefault_withoutAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.PUBLIC_SIGNUP_STATUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true));
    }

    // ─── GET /api/super-admin/signups ─────────────────────────────────────────

    @Test
    @DisplayName("list_shouldReturnTheListsSignupsNewestFirst_withAllFields_forSuperUser")
    void list_shouldReturnTheListsSignupsNewestFirst_withAllFields_forSuperUser() throws Exception {
        UUID orgId = organisationRepository.save(OrganisationModel.builder()
                .name("Acme Corp").slug("ACM").createdBy(userId).build()).getId();
        UUID oldId = insertSignup(EARLY_ACCESS, "old@example.com", "2026-11-01T08:00:00Z");
        UUID newId = insertSignup(EARLY_ACCESS, "new@example.com", "2026-11-20T08:00:00Z");
        insertSignup(OTHER_LIST, "other@example.com", "2026-11-25T08:00:00Z");
        dsl.update(EMAIL_SIGNUPS)
                .set(EMAIL_SIGNUPS.CODE, "TRIAL")
                .set(EMAIL_SIGNUPS.LOCALE, "en")
                .set(EMAIL_SIGNUPS.UTM_SOURCE, "linkedin")
                .set(EMAIL_SIGNUPS.UTM_MEDIUM, "social")
                .set(EMAIL_SIGNUPS.UTM_CAMPAIGN, "launch")
                .set(EMAIL_SIGNUPS.REDEEMED_AT, OffsetDateTime.parse("2026-12-01T10:00:00Z"))
                .set(EMAIL_SIGNUPS.REDEEMED_ORG_ID, orgId)
                .where(EMAIL_SIGNUPS.ID.eq(newId))
                .execute();

        mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS, EARLY_ACCESS)).with(asSuperUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newId.toString()))
                .andExpect(jsonPath("$[0].list").value(EARLY_ACCESS))
                .andExpect(jsonPath("$[0].email").value("new@example.com"))
                .andExpect(jsonPath("$[0].code").value("TRIAL"))
                .andExpect(jsonPath("$[0].redeemedAt").value("2026-12-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].redeemedOrgId").value(orgId.toString()))
                .andExpect(jsonPath("$[0].locale").value("en"))
                .andExpect(jsonPath("$[0].utmSource").value("linkedin"))
                .andExpect(jsonPath("$[0].utmMedium").value("social"))
                .andExpect(jsonPath("$[0].utmCampaign").value("launch"))
                .andExpect(jsonPath("$[0].consentAt").value("2026-11-20T08:00:00Z"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-20T08:00:00Z"))
                .andExpect(jsonPath("$[1].id").value(oldId.toString()))
                .andExpect(jsonPath("$[1].code").isEmpty())
                .andExpect(jsonPath("$[1].redeemedAt").isEmpty());
    }

    @Test
    @DisplayName("list_shouldReturn400_whenListIsUnknownOrMissing")
    void list_shouldReturn400_whenListIsUnknownOrMissing() throws Exception {
        mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS, "newsletter")).with(asSuperUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown sign-up list"));
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_SIGNUPS).with(asSuperUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown sign-up list"));
    }

    @Test
    @DisplayName("list_shouldReturn403_forNonSuperUser_and401_withoutAuth")
    void list_shouldReturn403_forNonSuperUser_and401_withoutAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS, EARLY_ACCESS)).with(asUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Super admin access required"));
        mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS, EARLY_ACCESS)))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/super-admin/signups/export ──────────────────────────────────

    @Test
    @DisplayName("export_shouldReturnCsvAttachment_withHeaderQuotingAndInjectionGuard_forSuperUser")
    void export_shouldReturnCsvAttachment_withHeaderQuotingAndInjectionGuard_forSuperUser() throws Exception {
        Map<String, Object> risky = signup("=cmd@example.com");
        risky.put("utmSource", "+ads");
        risky.put("utmMedium", "-social");
        risky.put("utmCampaign", "@launch, \"q4\"\nline2");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, risky)).andExpect(status().isNoContent());
        insertSignup(OTHER_LIST, "other@example.com", "2026-11-25T08:00:00Z");
        EmailSignupsRecord row = dsl.selectFrom(EMAIL_SIGNUPS).where(EMAIL_SIGNUPS.LIST.eq(EARLY_ACCESS)).fetchSingle();

        String expectedFilename = "attachment; filename=\"early-access-" + LocalDate.now(ZoneOffset.UTC) + ".csv\"";
        String csv = mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS_EXPORT, EARLY_ACCESS))
                        .with(asSuperUser()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv;charset=UTF-8")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedFilename))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).isEqualTo("id,list,email,code,redeemed_at,redeemed_org_id,locale,"
                + "utm_source,utm_medium,utm_campaign,consent_at,created_at\r\n"
                + row.getId() + ",early-access,'=cmd@example.com,,,,,'+ads,'-social,"
                + "\"'@launch, \"\"q4\"\"\nline2\","
                + row.getConsentAt().toInstant() + "," + row.getCreatedAt().toInstant() + "\r\n");
    }

    @Test
    @DisplayName("export_shouldGuardCellsStartingWithTabOrCarriageReturn")
    void export_shouldGuardCellsStartingWithTabOrCarriageReturn() throws Exception {
        Map<String, Object> risky = signup("tab@example.com");
        risky.put("utmSource", "\tads");
        risky.put("utmMedium", "\rsocial");
        risky.put("utmCampaign", "a\tb");
        mockMvc.perform(postJson(ApiPaths.PUBLIC_SIGNUPS, risky)).andExpect(status().isNoContent());
        EmailSignupsRecord row = dsl.selectFrom(EMAIL_SIGNUPS).fetchSingle();

        String csv = mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS_EXPORT, EARLY_ACCESS))
                        .with(asSuperUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The carriage return is also a line break, so that cell is quoted too. A tab inside a cell isn't guarded.
        assertThat(csv).endsWith("\r\n" + row.getId() + ",early-access,tab@example.com,,,,,"
                + "'\tads,\"'\rsocial\",a\tb,"
                + row.getConsentAt().toInstant() + "," + row.getCreatedAt().toInstant() + "\r\n");
    }

    @Test
    @DisplayName("export_shouldReturnOnlyTheHeader_whenListIsEmpty")
    void export_shouldReturnOnlyTheHeader_whenListIsEmpty() throws Exception {
        mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS_EXPORT, EARLY_ACCESS)).with(asSuperUser()))
                .andExpect(status().isOk())
                .andExpect(content().string("id,list,email,code,redeemed_at,redeemed_org_id,locale,"
                        + "utm_source,utm_medium,utm_campaign,consent_at,created_at\r\n"));
    }

    @Test
    @DisplayName("export_shouldReturn400_whenListIsUnknown_and403_forNonSuperUser")
    void export_shouldReturn400_whenListIsUnknown_and403_forNonSuperUser() throws Exception {
        mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS_EXPORT, "newsletter")).with(asSuperUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown sign-up list"));
        mockMvc.perform(get(ApiPaths.forList(ApiPaths.SUPER_ADMIN_SIGNUPS_EXPORT, EARLY_ACCESS)).with(asUser()))
                .andExpect(status().isForbidden());
    }

    // ─── DELETE /api/super-admin/signups/{id} ─────────────────────────────────

    @Test
    @DisplayName("delete_shouldReturn204AndRemoveTheRow_forSuperUser")
    void delete_shouldReturn204AndRemoveTheRow_forSuperUser() throws Exception {
        UUID id = insertSignup(EARLY_ACCESS, "ana@example.com", "2026-11-20T08:00:00Z");
        UUID keptId = insertSignup(EARLY_ACCESS, "bob@example.com", "2026-11-21T08:00:00Z");

        mockMvc.perform(delete(ApiPaths.superAdminSignup(id)).with(asSuperUser()))
                .andExpect(status().isNoContent());

        assertThat(dsl.selectFrom(EMAIL_SIGNUPS).fetch(EMAIL_SIGNUPS.ID)).containsExactly(keptId);
    }

    @Test
    @DisplayName("delete_shouldReturn404_whenRowIsMissing")
    void delete_shouldReturn404_whenRowIsMissing() throws Exception {
        mockMvc.perform(delete(ApiPaths.superAdminSignup(UUID.randomUUID())).with(asSuperUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Sign-up not found"));
    }

    @Test
    @DisplayName("delete_shouldReturn403AndKeepTheRow_forNonSuperUser")
    void delete_shouldReturn403AndKeepTheRow_forNonSuperUser() throws Exception {
        UUID id = insertSignup(EARLY_ACCESS, "ana@example.com", "2026-11-20T08:00:00Z");

        mockMvc.perform(delete(ApiPaths.superAdminSignup(id)).with(asUser()))
                .andExpect(status().isForbidden());

        assertThat(dsl.fetchCount(EMAIL_SIGNUPS)).isEqualTo(1);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> signup(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("list", EARLY_ACCESS);
        body.put("email", email);
        body.put("consent", true);
        return body;
    }

    private MockHttpServletRequestBuilder postJson(String path, Map<String, Object> body) throws Exception {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private UUID insertSignup(String list, String email, String at) {
        OffsetDateTime time = OffsetDateTime.parse(at);
        return dsl.insertInto(EMAIL_SIGNUPS)
                .set(EMAIL_SIGNUPS.LIST, list)
                .set(EMAIL_SIGNUPS.EMAIL, email)
                .set(EMAIL_SIGNUPS.CONSENT_AT, time)
                .set(EMAIL_SIGNUPS.CREATED_AT, time)
                .returning(EMAIL_SIGNUPS.ID)
                .fetchSingle()
                .getId();
    }

    private RequestPostProcessor asSuperUser() {
        return jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com"));
    }

    private RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("email", "user@example.com"));
    }
}
