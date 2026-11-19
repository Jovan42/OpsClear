package com.opsclear.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.opsclear.event.InviteEmailListener;
import com.opsclear.event.InviteEmailRequestedEvent;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Org invite endpoint")
class OrgInviteIntegrationTest {

    private static final long MAIL_WAIT_MS = 5_000;
    private static final Duration OUTSIDE_COOLDOWN = Duration.ofMinutes(6);

    // In-memory SMTP on the test profile's spring.mail.port (3025), fresh for every test (JOB-342)
    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrgInviteRepository orgInviteRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private InviteEmailListener inviteEmailListener;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID orgId;

    // Accepting an invite creates a notification (JOB-279): leave none behind, or they block
    // other classes from deleting organisations, projects and users.
    @AfterEach
    void deleteNotifications() {
        notificationRepository.deleteAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        orgInviteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(adminId).email("admin@example.com").name("Admin").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());

        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        orgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        organisationRepository.saveMember(orgId, adminId, OrganisationRole.ADMIN);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
    }

    // ─── sendInvite ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendInvite_shouldReturn201_whenOwnerInvitesNewEmail")
    void sendInvite_shouldReturn201_whenOwnerInvitesNewEmail() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.organisationId").value(orgId.toString()))
                .andExpect(jsonPath("$.invitedByName").value("Owner"));
    }

    @Test
    @DisplayName("sendInvite_shouldReturn201_whenAdminInvitesNewEmail")
    void sendInvite_shouldReturn201_whenAdminInvitesNewEmail() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(adminId.toString())
                                .claim("email", "admin@example.com").claim("name", "Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    @DisplayName("sendInvite_shouldReturn403_whenCallerIsMember")
    void sendInvite_shouldReturn403_whenCallerIsMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn409_whenEmailAlreadyMember")
    void sendInvite_shouldReturn409_whenEmailAlreadyMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn404_whenOrgDoesNotExist")
    void sendInvite_shouldReturn404_whenOrgDoesNotExist() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn403_whenCallerIsNotMember")
    void sendInvite_shouldReturn403_whenCallerIsNotMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn409_whenPendingInviteAlreadyExists")
    void sendInvite_shouldReturn409_whenPendingInviteAlreadyExists() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("sendInvite_shouldReturn400_whenEmailIsBlank")
    void sendInvite_shouldReturn400_whenEmailIsBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sendInvite_shouldReturnTokenAndSaveLastSentAt_whenInviteCreated")
    void sendInvite_shouldReturnTokenAndSaveLastSentAt_whenInviteCreated() throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        JsonNode body = createInvite("newuser@example.com", null);

        UUID inviteId = UUID.fromString(body.get("id").asText());
        OrgInviteModel saved = orgInviteRepository.findById(inviteId).orElseThrow();
        assertThat(body.get("token").asText()).hasSize(64).isEqualTo(saved.getToken());
        assertThat(saved.getLastSentAt()).isNotNull()
                .isAfterOrEqualTo(before.minusSeconds(1))
                .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("sendInvite_shouldEmailInviteInSerbian_whenLanguageIsSr")
    void sendInvite_shouldEmailInviteInSerbian_whenLanguageIsSr() throws Exception {
        JsonNode body = createInvite("novi@example.com", "sr");
        String token = body.get("token").asText();

        MimeMessage email = singleEmail();
        assertThat(email.getAllRecipients()).extracting(Object::toString).containsExactly("novi@example.com");
        assertThat(email.getSubject()).isEqualTo("Pozvani ste da se pridružite organizaciji Acme Corp na OpsClear-u");
        assertThat(part(email, "text/plain"))
                .contains("Owner vas poziva da se pridružite organizaciji Acme Corp na OpsClear-u.")
                .contains("http://app.test/invite/" + token);
        assertThat(part(email, "text/html"))
                .contains("href=\"http://app.test/invite/" + token + "\"")
                .contains("background-color:#059669")
                .contains("Ako dugme ne radi, kopirajte ovaj link u pregledač:<br>"
                        + "<span style=\"word-break:break-all;\">http://app.test/invite/" + token + "</span>");
    }

    @Test
    @DisplayName("sendInvite_shouldEmailInviteInEnglish_whenLanguageMissing")
    void sendInvite_shouldEmailInviteInEnglish_whenLanguageMissing() throws Exception {
        JsonNode body = createInvite("newuser@example.com", null);
        String token = body.get("token").asText();

        MimeMessage email = singleEmail();
        assertThat(email.getAllRecipients()).extracting(Object::toString).containsExactly("newuser@example.com");
        assertThat(email.getSubject()).isEqualTo("You're invited to join Acme Corp on OpsClear");
        assertThat(part(email, "text/plain"))
                .contains("Owner has invited you to join Acme Corp on OpsClear.")
                .contains("http://app.test/invite/" + token);
        assertThat(part(email, "text/html"))
                .contains("max-width:560px; background-color:#ffffff; border:1px solid #e5e7eb; border-radius:12px;")
                .contains("If the button doesn't work, copy this link into your browser:<br>"
                        + "<span style=\"word-break:break-all;\">http://app.test/invite/" + token + "</span>");
    }

    @Test
    @DisplayName("sendInvite_shouldEmailInviteInEnglish_whenLanguageIsEn")
    void sendInvite_shouldEmailInviteInEnglish_whenLanguageIsEn() throws Exception {
        createInvite("newuser@example.com", "en");

        assertThat(singleEmail().getSubject()).isEqualTo("You're invited to join Acme Corp on OpsClear");
    }

    @Test
    @DisplayName("sendInvite_shouldReturn400AndSendNothing_whenLanguageUnsupported")
    void sendInvite_shouldReturn400AndSendNothing_whenLanguageUnsupported() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "newuser@example.com", "language", "fr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("language: Language must be en or sr"));

        assertThat(orgInviteRepository.findPendingByOrgId(orgId)).isEmpty();
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("sendInvite_shouldSendNoEmail_whenInviteRejected")
    void sendInvite_shouldSendNoEmail_whenInviteRejected() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com"))))
                .andExpect(status().isConflict());

        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("sendInvite_shouldStillReturn201_whenSmtpServerIsDown")
    void sendInvite_shouldStillReturn201_whenSmtpServerIsDown() throws Exception {
        GREEN_MAIL.stop();

        JsonNode body = createInvite("newuser@example.com", "en");

        UUID inviteId = UUID.fromString(body.get("id").asText());
        assertThat(orgInviteRepository.findById(inviteId)).isPresent();
    }

    @Test
    @DisplayName("inviteEmailListener_shouldSendNothing_whenInviteRevokedOrMissing")
    void inviteEmailListener_shouldSendNothing_whenInviteRevokedOrMissing() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());
        assertThat(GREEN_MAIL.waitForIncomingEmail(MAIL_WAIT_MS, 1)).isTrue();
        orgInviteRepository.markRevoked(inviteId);

        inviteEmailListener.onInviteEmailRequested(new InviteEmailRequestedEvent(inviteId, "en"));
        inviteEmailListener.onInviteEmailRequested(new InviteEmailRequestedEvent(UUID.randomUUID(), "en"));

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("inviteEmailListener_shouldSendNothing_whenOrganisationDeleted")
    void inviteEmailListener_shouldSendNothing_whenOrganisationDeleted() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());
        assertThat(GREEN_MAIL.waitForIncomingEmail(MAIL_WAIT_MS, 1)).isTrue();
        OrganisationModel org = organisationRepository.findById(orgId).orElseThrow();
        org.softDelete();
        organisationRepository.save(org);

        inviteEmailListener.onInviteEmailRequested(new InviteEmailRequestedEvent(inviteId, "en"));

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("repository_shouldReadBackLastSentAt_whenRowHasItSet")
    void repository_shouldReadBackLastSentAt_whenRowHasItSet() {
        Instant lastSentAt = Instant.parse("2026-11-10T08:30:15.123456Z");
        OrgInviteModel saved = orgInviteRepository.save(OrgInviteModel.builder()
                .organisationId(orgId)
                .email("stored@example.com")
                .invitedBy(ownerId)
                .token("stored-token")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .lastSentAt(lastSentAt)
                .build());

        assertThat(saved.getLastSentAt()).isEqualTo(lastSentAt);
        assertThat(orgInviteRepository.findById(saved.getId()).orElseThrow().getLastSentAt()).isEqualTo(lastSentAt);
        assertThat(orgInviteRepository.findByToken("stored-token").orElseThrow().getLastSentAt())
                .isEqualTo(lastSentAt);
        assertThat(orgInviteRepository.findPendingByOrgId(orgId)).singleElement()
                .extracting(OrgInviteModel::getLastSentAt).isEqualTo(lastSentAt);
    }

    @Test
    @DisplayName("repository_shouldReadBackNullLastSentAt_whenRowHasNone")
    void repository_shouldReadBackNullLastSentAt_whenRowHasNone() {
        OrgInviteModel saved = orgInviteRepository.save(OrgInviteModel.builder()
                .organisationId(orgId)
                .email("legacy@example.com")
                .invitedBy(ownerId)
                .token("legacy-token")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        assertThat(orgInviteRepository.findById(saved.getId()).orElseThrow().getLastSentAt()).isNull();
    }

    // ─── listInvites ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listInvites_shouldReturnPendingInvites_whenCallerIsOwner")
    void listInvites_shouldReturnPendingInvites_whenCallerIsOwner() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "invite1@example.com"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("invite1@example.com"))
                .andExpect(jsonPath("$[0].token").value(orgInviteRepository.findPendingByOrgId(orgId).get(0).getToken()));
    }

    @Test
    @DisplayName("listInvites_shouldReturn403_whenCallerIsMember")
    void listInvites_shouldReturn403_whenCallerIsMember() throws Exception {
        mockMvc.perform(get(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isForbidden());
    }

    // ─── revokeInvite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeInvite_shouldReturn204_whenOwnerRevokes")
    void revokeInvite_shouldReturn204_whenOwnerRevokes() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "invited@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID inviteId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("revokeInvite_shouldReturn400_whenInviteAlreadyRevoked")
    void revokeInvite_shouldReturn400_whenInviteAlreadyRevoked() throws Exception {
        String createResponse = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "invited@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID inviteId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("revokeInvite_shouldReturn404_whenInviteNotFound")
    void revokeInvite_shouldReturn404_whenInviteNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.orgInvite(orgId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }

    // ─── resendInvite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("resendInvite_shouldReturn200AndDeliverSecondEmail_whenCooldownOver")
    void resendInvite_shouldReturn200AndDeliverSecondEmail_whenCooldownOver() throws Exception {
        JsonNode created = createInvite("newuser@example.com", "en");
        UUID inviteId = UUID.fromString(created.get("id").asText());
        String token = created.get("token").asText();
        singleEmail();
        moveLastSentAtBack(inviteId, OUTSIDE_COOLDOWN);
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId))
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("language", "sr"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inviteId.toString()))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.expiresAt").value(created.get("expiresAt").asText()));

        OrgInviteModel saved = orgInviteRepository.findById(inviteId).orElseThrow();
        assertThat(saved.getLastSentAt()).isAfterOrEqualTo(before.minusSeconds(1)).isBeforeOrEqualTo(Instant.now());
        assertThat(saved.getToken()).isEqualTo(token);

        assertThat(GREEN_MAIL.waitForIncomingEmail(MAIL_WAIT_MS, 2)).as("resent email received").isTrue();
        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(2);
        MimeMessage resent = received[1];
        assertThat(resent.getAllRecipients()).extracting(Object::toString).containsExactly("newuser@example.com");
        assertThat(resent.getSubject()).isEqualTo("Pozvani ste da se pridružite organizaciji Acme Corp na OpsClear-u");
        assertThat(part(resent, "text/plain")).contains("http://app.test/invite/" + token);
    }

    @Test
    @DisplayName("resendInvite_shouldSendEnglishEmail_whenAdminResendsWithoutBody")
    void resendInvite_shouldSendEnglishEmail_whenAdminResendsWithoutBody() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "sr").get("id").asText());
        singleEmail();
        moveLastSentAtBack(inviteId, OUTSIDE_COOLDOWN);

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(adminId.toString())
                                .claim("email", "admin@example.com").claim("name", "Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inviteId.toString()));

        assertThat(GREEN_MAIL.waitForIncomingEmail(MAIL_WAIT_MS, 2)).as("resent email received").isTrue();
        assertThat(GREEN_MAIL.getReceivedMessages()[1].getSubject())
                .isEqualTo("You're invited to join Acme Corp on OpsClear");
    }

    @Test
    @DisplayName("resendInvite_shouldReturn200_whenInviteWasNeverEmailed")
    void resendInvite_shouldReturn200_whenInviteWasNeverEmailed() throws Exception {
        OrgInviteModel legacy = orgInviteRepository.save(OrgInviteModel.builder()
                .organisationId(orgId)
                .email("legacy@example.com")
                .invitedBy(ownerId)
                .token("legacy-token")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, legacy.getId()))
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("language", "en"))))
                .andExpect(status().isOk());

        assertThat(orgInviteRepository.findById(legacy.getId()).orElseThrow().getLastSentAt()).isNotNull();
        assertThat(singleEmail().getAllRecipients()).extracting(Object::toString)
                .containsExactly("legacy@example.com");
    }

    @Test
    @DisplayName("resendInvite_shouldReturn429WithRetryAfter_whenInsideCooldown")
    void resendInvite_shouldReturn429WithRetryAfter_whenInsideCooldown() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());
        singleEmail();
        moveLastSentAtBack(inviteId, Duration.ofMinutes(1));
        Instant lastSentAt = orgInviteRepository.findById(inviteId).orElseThrow().getLastSentAt();

        MvcResult result = mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId))
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("language", "en"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(ErrorMessages.OrgInvite.RESEND_COOLDOWN))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();

        long retryAfter = Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isBetween(230L, 240L);
        assertThat(orgInviteRepository.findById(inviteId).orElseThrow().getLastSentAt()).isEqualTo(lastSentAt);
        assertThat(GREEN_MAIL.waitForIncomingEmail(1_000, 2)).as("no second email").isFalse();
    }

    @Test
    @DisplayName("resendInvite_shouldReturn429_whenResentRightAfterCreate")
    void resendInvite_shouldReturn429_whenResentRightAfterCreate() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId)).with(ownerJwt()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("29\\d|300")));
    }

    @Test
    @DisplayName("resendInvite_shouldReturn409_whenInviteRevoked")
    void resendInvite_shouldReturn409_whenInviteRevoked() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());
        singleEmail();
        orgInviteRepository.markRevoked(inviteId);

        assertResendConflict(inviteId);
    }

    @Test
    @DisplayName("resendInvite_shouldReturn409_whenInviteAccepted")
    void resendInvite_shouldReturn409_whenInviteAccepted() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());
        singleEmail();
        orgInviteRepository.markAccepted(inviteId);

        assertResendConflict(inviteId);
    }

    @Test
    @DisplayName("resendInvite_shouldReturn409_whenInviteExpired")
    void resendInvite_shouldReturn409_whenInviteExpired() throws Exception {
        OrgInviteModel expired = orgInviteRepository.save(OrgInviteModel.builder()
                .organisationId(orgId)
                .email("expired@example.com")
                .invitedBy(ownerId)
                .token("expired-token")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .lastSentAt(Instant.now().minus(8, ChronoUnit.DAYS))
                .build());

        assertResendConflict(expired.getId());
    }

    @Test
    @DisplayName("resendInvite_shouldReturn403_whenCallerIsMember")
    void resendInvite_shouldReturn403_whenCallerIsMember() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());
        singleEmail();
        moveLastSentAtBack(inviteId, OUTSIDE_COOLDOWN);
        Instant lastSentAt = orgInviteRepository.findById(inviteId).orElseThrow().getLastSentAt();

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        assertThat(orgInviteRepository.findById(inviteId).orElseThrow().getLastSentAt()).isEqualTo(lastSentAt);
    }

    @Test
    @DisplayName("resendInvite_shouldReturn403_whenCallerIsNotMember")
    void resendInvite_shouldReturn403_whenCallerIsNotMember() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resendInvite_shouldReturn404_whenInviteBelongsToAnotherOrg")
    void resendInvite_shouldReturn404_whenInviteBelongsToAnotherOrg() throws Exception {
        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Other Corp", "slug", "OTH"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID otherOrgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        OrgInviteModel otherInvite = orgInviteRepository.save(OrgInviteModel.builder()
                .organisationId(otherOrgId)
                .email("someone@example.com")
                .invitedBy(outsiderId)
                .token("other-org-token")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .lastSentAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, otherInvite.getId())).with(ownerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorMessages.OrgInvite.NOT_FOUND));

        assertThat(GREEN_MAIL.waitForIncomingEmail(1_000, 1)).as("no email").isFalse();
    }

    @Test
    @DisplayName("resendInvite_shouldReturn404_whenInviteDoesNotExist")
    void resendInvite_shouldReturn404_whenInviteDoesNotExist() throws Exception {
        mockMvc.perform(post(ApiPaths.resendInvite(orgId, UUID.randomUUID())).with(ownerJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("resendInvite_shouldReturn404_whenOrgDoesNotExist")
    void resendInvite_shouldReturn404_whenOrgDoesNotExist() throws Exception {
        mockMvc.perform(post(ApiPaths.resendInvite(UUID.randomUUID(), UUID.randomUUID())).with(ownerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Organisation.NOT_FOUND));
    }

    @Test
    @DisplayName("resendInvite_shouldReturn400_whenLanguageUnsupported")
    void resendInvite_shouldReturn400_whenLanguageUnsupported() throws Exception {
        UUID inviteId = UUID.fromString(createInvite("newuser@example.com", "en").get("id").asText());

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId))
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("language", "fr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("language: Language must be en or sr"));
    }

    // ─── acceptInvite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptInvite_shouldReturn200_whenTokenValidAndEmailMatches")
    void acceptInvite_shouldReturn200_whenTokenValidAndEmailMatches() throws Exception {
        UUID newUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(newUserId).email("newuser@example.com").name("New User").build());

        String createResponse = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "newuser@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(createResponse).get("token").asText();

        mockMvc.perform(post(ApiPaths.acceptInvite(token))
                        .with(jwt().jwt(j -> j.subject(newUserId.toString())
                                .claim("email", "newuser@example.com").claim("name", "New User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    @DisplayName("acceptInvite_shouldReturn403_whenEmailDoesNotMatch")
    void acceptInvite_shouldReturn403_whenEmailDoesNotMatch() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "specific@example.com"))))
                .andExpect(status().isCreated());

        String token = orgInviteRepository.findPendingByOrgId(orgId).get(0).getToken();

        mockMvc.perform(post(ApiPaths.acceptInvite(token))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("acceptInvite_shouldReturn404_whenTokenDoesNotExist")
    void acceptInvite_shouldReturn404_whenTokenDoesNotExist() throws Exception {
        mockMvc.perform(post(ApiPaths.acceptInvite("nonexistent-token"))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("acceptInvite_shouldReturn409_whenUserAlreadyMember")
    void acceptInvite_shouldReturn409_whenUserAlreadyMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "outsider@example.com"))))
                .andExpect(status().isCreated());

        String token = orgInviteRepository.findPendingByOrgId(orgId).get(0).getToken();

        organisationRepository.saveMember(orgId, outsiderId, OrganisationRole.MEMBER);

        mockMvc.perform(post(ApiPaths.acceptInvite(token))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isConflict());
    }

    // JOB-392: the app assumes one organisation per user (JOB-241), so accepting an invite
    // while already in another organisation must fail like creating a second one does
    @Test
    @DisplayName("acceptInvite_shouldReturn409AndNotJoin_whenUserBelongsToAnotherOrganisation")
    void acceptInvite_shouldReturn409AndNotJoin_whenUserBelongsToAnotherOrganisation() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Pine Studio", "slug", "PIN"))))
                .andExpect(status().isCreated());

        OrgInviteModel invite = orgInviteRepository.findByToken(
                createInvite("outsider@example.com", null).get("token").asText()).orElseThrow();

        mockMvc.perform(post(ApiPaths.acceptInvite(invite.getToken()))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString())
                                .claim("email", "outsider@example.com").claim("name", "Outsider"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Organisation.ALREADY_IN_ORGANISATION));

        assertThat(organisationRepository.existsMember(orgId, outsiderId)).as("joined invite org").isFalse();
        assertThat(orgInviteRepository.findById(invite.getId()).orElseThrow().isPending())
                .as("invite still pending").isTrue();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private JsonNode createInvite(String email, String language) throws Exception {
        Map<String, String> request = language == null
                ? Map.of("email", email)
                : Map.of("email", email, "language", language);
        String response = mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private RequestPostProcessor ownerJwt() {
        return jwt().jwt(j -> j.subject(ownerId.toString())
                .claim("email", "owner@example.com").claim("name", "Owner"));
    }

    private void moveLastSentAtBack(UUID inviteId, Duration ago) {
        orgInviteRepository.updateLastSentAt(inviteId, Instant.now().minus(ago));
    }

    private void assertResendConflict(UUID inviteId) throws Exception {
        int emailsBefore = GREEN_MAIL.getReceivedMessages().length;

        mockMvc.perform(post(ApiPaths.resendInvite(orgId, inviteId))
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("language", "en"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(ErrorMessages.OrgInvite.INVALID));

        assertThat(GREEN_MAIL.waitForIncomingEmail(1_000, emailsBefore + 1)).as("no resent email").isFalse();
    }

    // The send runs after commit on the notification executor, so wait for it instead of asserting straight away
    private static MimeMessage singleEmail() {
        assertThat(GREEN_MAIL.waitForIncomingEmail(MAIL_WAIT_MS, 1)).as("invite email received").isTrue();
        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(1);
        return received[0];
    }

    private static String part(Part message, String mimeType) throws Exception {
        List<String> found = new ArrayList<>();
        collectParts(message, mimeType, found);
        assertThat(found).as("%s parts", mimeType).hasSize(1);
        return found.get(0);
    }

    private static void collectParts(Part part, String mimeType, List<String> found) throws Exception {
        if (part.isMimeType(mimeType)) {
            found.add((String) part.getContent());
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collectParts(multipart.getBodyPart(i), mimeType, found);
            }
        }
    }
}
