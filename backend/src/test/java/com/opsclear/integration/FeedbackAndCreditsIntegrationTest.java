package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FeedbackSubmissionRepository;
import com.opsclear.repository.OrgCreditRepository;
import com.opsclear.repository.OrganisationRepository;
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

import java.util.Map;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.USERS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Feedback submissions + credits API")
class FeedbackAndCreditsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private FeedbackSubmissionRepository feedbackSubmissionRepository;
    @Autowired private OrgCreditRepository orgCreditRepository;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID superUserId;
    private UUID orgId;
    private UUID otherOrgId;

    @BeforeEach
    void setUp() throws Exception {
        // users first: TRUNCATE ... CASCADE (see UserRepository.deleteAll) clears the
        // entire FK graph transitively, so it must run before the narrower deletes
        // below — otherwise a project left behind by another test class (which this
        // class never touches directly) can block organisationRepository.deleteAll().
        userRepository.deleteAll();
        orgCreditRepository.deleteAll();
        feedbackSubmissionRepository.deleteAll();
        organisationRepository.deleteAll();

        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();
        superUserId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(adminId).email("admin@example.com").name("Admin").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());
        userRepository.save(UserModel.builder().id(superUserId).email("super@example.com").name("Super").build());
        dsl.update(USERS).set(USERS.SUPER_USER, true).where(USERS.ID.eq(superUserId)).execute();

        orgId = createOrg(ownerId, "owner@example.com", "Acme Corp", "ACM");
        addMember(orgId, ownerId, adminId, "ADMIN");
        addMember(orgId, ownerId, memberId, "MEMBER");

        otherOrgId = createOrg(outsiderId, "outsider@example.com", "Other Corp", "OTH");
    }

    private UUID createOrg(UUID callerId, String email, String name, String slug) throws Exception {
        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", email)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addMember(UUID orgId, UUID ownerId, UUID userId, String role) throws Exception {
        mockMvc.perform(post(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", userId, "role", role))))
                .andExpect(status().isCreated());
    }

    private UUID submitFeedback(UUID callerId, String email, String type, String title, String description)
            throws Exception {
        String response = mockMvc.perform(post(ApiPaths.FEEDBACK)
                        .with(jwt().jwt(j -> j.subject(callerId.toString()).claim("email", email)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("type", type, "title", title, "description", description))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // ─── POST /api/feedback ────────────────────────────────────────────────────

    @Test
    @DisplayName("submit_shouldReturn201_andCreateSubmission_forOrgMember")
    void submit_shouldReturn201_andCreateSubmission_forOrgMember() throws Exception {
        mockMvc.perform(post(ApiPaths.FEEDBACK)
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("type", "BUG", "title", "Broken button", "description", "It does nothing"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                .andExpect(jsonPath("$.submittedBy").value(memberId.toString()))
                .andExpect(jsonPath("$.type").value("BUG"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("submit_shouldReturn400_whenTitleBlank")
    void submit_shouldReturn400_whenTitleBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.FEEDBACK)
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("type", "BUG", "title", "", "description", "Description"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("submit_shouldReturn404_whenCallerHasNoOrg")
    void submit_shouldReturn404_whenCallerHasNoOrg() throws Exception {
        UUID orphanId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(orphanId).email("orphan@example.com").name("Orphan").build());

        mockMvc.perform(post(ApiPaths.FEEDBACK)
                        .with(jwt().jwt(j -> j.subject(orphanId.toString()).claim("email", "orphan@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("type", "OTHER", "title", "Title", "description", "Description"))))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/feedback/mine ────────────────────────────────────────────────

    @Test
    @DisplayName("listMine_shouldReturnOnlyCallersSubmissions")
    void listMine_shouldReturnOnlyCallersSubmissions() throws Exception {
        submitFeedback(memberId, "member@example.com", "BUG", "Member's bug", "Description");
        submitFeedback(ownerId, "owner@example.com", "FEATURE", "Owner's feature", "Description");

        mockMvc.perform(get(ApiPaths.FEEDBACK_MINE)
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Member's bug"));
    }

    // ─── GET /api/organisations/{orgId}/credits/balance ────────────────────────

    @Test
    @DisplayName("getBalance_shouldReturnZero_whenNoCreditsGranted")
    void getBalance_shouldReturnZero_whenNoCreditsGranted() throws Exception {
        mockMvc.perform(get(ApiPaths.orgCreditsBalance(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("getBalance_shouldReturn200_forAdmin")
    void getBalance_shouldReturn200_forAdmin() throws Exception {
        mockMvc.perform(get(ApiPaths.orgCreditsBalance(orgId))
                        .with(jwt().jwt(j -> j.subject(adminId.toString()).claim("email", "admin@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getBalance_shouldReturn403_forPlainMember")
    void getBalance_shouldReturn403_forPlainMember() throws Exception {
        mockMvc.perform(get(ApiPaths.orgCreditsBalance(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getBalance_shouldReturn404_whenCallerIsNotAMember")
    void getBalance_shouldReturn404_whenCallerIsNotAMember() throws Exception {
        mockMvc.perform(get(ApiPaths.orgCreditsBalance(orgId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/super-admin/feedback ─────────────────────────────────────────

    @Test
    @DisplayName("superAdminListFeedback_shouldReturn200_andIncludeSubmissionsAcrossOrgs")
    void superAdminListFeedback_shouldReturn200_andIncludeSubmissionsAcrossOrgs() throws Exception {
        submitFeedback(memberId, "member@example.com", "BUG", "Bug in org 1", "Description");
        submitFeedback(outsiderId, "outsider@example.com", "FEATURE", "Feature in org 2", "Description");

        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_FEEDBACK)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("superAdminListFeedback_shouldReturn403_forRegularUser")
    void superAdminListFeedback_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_FEEDBACK)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isForbidden());
    }

    // ─── POST /api/super-admin/credits/grant ───────────────────────────────────

    @Test
    @DisplayName("grantCredit_shouldReturn201_andCreateDiscretionaryLedgerEntry")
    void grantCredit_shouldReturn201_andCreateDiscretionaryLedgerEntry() throws Exception {
        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orgId", orgId, "amount", 500, "reason", "Referral bonus"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                .andExpect(jsonPath("$.amount").value(500))
                .andExpect(jsonPath("$.submissionId").doesNotExist());

        mockMvc.perform(get(ApiPaths.orgCreditsBalance(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500));
    }

    @Test
    @DisplayName("grantCredit_shouldMarkSubmissionReviewed_whenSubmissionIdProvided")
    void grantCredit_shouldMarkSubmissionReviewed_whenSubmissionIdProvided() throws Exception {
        UUID submissionId = submitFeedback(memberId, "member@example.com", "BUG", "Great catch", "Description");

        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orgId", orgId, "amount", 1000, "reason", "Great bug report",
                                "submissionId", submissionId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submissionId").value(submissionId.toString()));

        mockMvc.perform(get(ApiPaths.FEEDBACK_MINE)
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REVIEWED"));
    }

    @Test
    @DisplayName("grantCredit_shouldReturn404_whenSubmissionBelongsToDifferentOrg")
    void grantCredit_shouldReturn404_whenSubmissionBelongsToDifferentOrg() throws Exception {
        UUID otherOrgSubmissionId = submitFeedback(outsiderId, "outsider@example.com", "OTHER", "Other org's idea", "Description");

        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orgId", orgId, "amount", 1000, "reason", "Mismatched org",
                                "submissionId", otherOrgSubmissionId))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("grantCredit_shouldReturn400_whenAmountNotPositive")
    void grantCredit_shouldReturn400_whenAmountNotPositive() throws Exception {
        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orgId", orgId, "amount", 0, "reason", "Zero credit"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("grantCredit_shouldReturn403_forRegularUser")
    void grantCredit_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orgId", orgId, "amount", 500, "reason", "Attempted self-grant"))))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/super-admin/organisations/{orgId}/credits ────────────────────

    @Test
    @DisplayName("getLedger_shouldReturn200_withAllEntries_forSuperUser")
    void getLedger_shouldReturn200_withAllEntries_forSuperUser() throws Exception {
        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orgId", orgId, "amount", 300, "reason", "First grant"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orgId", orgId, "amount", 200, "reason", "Second grant"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.superAdminOrgCredits(orgId))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("getLedger_shouldReturn403_forRegularUser")
    void getLedger_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(get(ApiPaths.superAdminOrgCredits(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isForbidden());
    }
}
