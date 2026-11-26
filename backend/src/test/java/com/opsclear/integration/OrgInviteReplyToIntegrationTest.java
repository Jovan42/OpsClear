package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.EmailService;
import com.opsclear.service.InviteEmail;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invite emails with {@code opsclear.mail.reply-to} set (ADR-0064, JOB-433): Reply-To with the From display name and
 * the support footer in the text and HTML parts, in en and sr. The blank case is in {@link OrgInviteIntegrationTest},
 * which runs on the test profile's empty value.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "opsclear.mail.reply-to=" + OrgInviteReplyToIntegrationTest.SUPPORT)
@DisplayName("Org invite email, Reply-To and support footer")
class OrgInviteReplyToIntegrationTest {

    static final String SUPPORT = "support@opsclear.test";
    private static final long MAIL_WAIT_MS = 5_000;
    private static final String MAILTO = "<a href=\"mailto:" + SUPPORT + "\" style=\"color:#059669;\">" + SUPPORT
            + "</a>";
    private static final String FOOTER_STYLE = "<p style=\"font-size:13px; color:#6b7280; margin:0;\">";

    // In-memory SMTP on the test profile's spring.mail.port (3025), fresh for every test
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

    private UUID ownerId;
    private UUID orgId;

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
        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());

        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    @DisplayName("sendInvite_shouldSetReplyToWithDisplayName_whenReplyToIsConfigured")
    void sendInvite_shouldSetReplyToWithDisplayName_whenReplyToIsConfigured() throws Exception {
        createInvite("newuser@example.com", "en");

        MimeMessage email = singleEmail();
        assertThat(email.getReplyTo()).hasSize(1);
        InternetAddress replyTo = (InternetAddress) email.getReplyTo()[0];
        assertThat(replyTo.getAddress()).isEqualTo(SUPPORT);
        assertThat(replyTo.getPersonal()).isEqualTo("OpsClear");
        InternetAddress from = (InternetAddress) email.getFrom()[0];
        assertThat(from.getAddress()).isNotEqualTo(SUPPORT);
    }

    @Test
    @DisplayName("sendInvite_shouldAddEnglishSupportFooter_whenReplyToIsConfigured")
    void sendInvite_shouldAddEnglishSupportFooter_whenReplyToIsConfigured() throws Exception {
        createInvite("newuser@example.com", "en");

        MimeMessage email = singleEmail();
        assertThat(part(email, "text/plain"))
                .matches("(?s).*you can ignore this email\\.\\r?\\n\\r?\\n"
                        + "Questions\\? Reply to this email or write to " + SUPPORT + "\\r?\\n");
        assertThat(part(email, "text/html"))
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0 0 16px;\">If you weren't expecting")
                // the footer is the last paragraph of the card
                .containsPattern(Pattern.quote(FOOTER_STYLE + "Questions? Reply to this email or write to " + MAILTO
                        + "</p>") + "\\r?\\n</td>");
    }

    @Test
    @DisplayName("sendInvite_shouldAddSerbianSupportFooter_whenReplyToIsConfigured")
    void sendInvite_shouldAddSerbianSupportFooter_whenReplyToIsConfigured() throws Exception {
        createInvite("novi@example.com", "sr");

        MimeMessage email = singleEmail();
        assertThat(email.getHeader("Reply-To")).hasSize(1);
        assertThat(part(email, "text/plain"))
                .matches("(?s).*zanemariti ovaj email\\.\\r?\\n\\r?\\n"
                        + "Imate pitanje\\? Odgovorite na ovaj mejl ili pišite na " + SUPPORT + "\\r?\\n");
        assertThat(part(email, "text/html"))
                .contains(FOOTER_STYLE + "Imate pitanje? Odgovorite na ovaj mejl ili pišite na " + MAILTO + "</p>")
                .doesNotContain("Questions?");
    }

    @Test
    @DisplayName("startup_shouldFail_whenReplyToIsMalformed")
    void startup_shouldFail_whenReplyToIsMalformed() {
        contextWithReplyTo("support@").run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().isInstanceOf(AddressException.class));
        contextWithReplyTo("support opsclear.test").run(context -> assertThat(context).hasFailed()
                .getFailure().hasStackTraceContaining("opsclear.mail.reply-to (SMTP_REPLY_TO) is not a valid email"));
    }

    @Test
    @DisplayName("startup_shouldSucceed_whenReplyToIsBlank")
    void startup_shouldSucceed_whenReplyToIsBlank() {
        contextWithReplyTo("").run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(EmailService.class));
    }

    @Test
    @DisplayName("sendInvite_shouldUseTrimmedAddress_whenReplyToHasSurroundingSpaces")
    void sendInvite_shouldUseTrimmedAddress_whenReplyToHasSurroundingSpaces() {
        contextWithReplyTo("  " + SUPPORT + " ").run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(EmailService.class).sendInvite(new InviteEmail(UUID.randomUUID(), "newuser@example.com",
                    "en", "Acme Corp", "Owner", "token", Instant.parse("2030-01-02T03:04:05Z")));

            MimeMessage email = singleEmail();
            assertThat(email.getHeader("Reply-To")).containsExactly("OpsClear <" + SUPPORT + ">");
            assertThat(part(email, "text/plain")).matches("(?s).*write to " + SUPPORT + "\\r?\\n");
            assertThat(part(email, "text/html")).contains(MAILTO);
        });
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    // Only EmailService and a mail sender pointed at GreenMail, with the same property binding as the app
    // (@Value placeholders)
    private static ApplicationContextRunner contextWithReplyTo(String replyTo) {
        return new ApplicationContextRunner()
                .withBean(JavaMailSenderImpl.class, () -> {
                    JavaMailSenderImpl sender = new JavaMailSenderImpl();
                    sender.setHost("localhost");
                    sender.setPort(GREEN_MAIL.getSmtp().getPort());
                    return sender;
                })
                .withBean(EmailService.class)
                // A raw property source, because "key=value" property strings are trimmed (an env var isn't)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("mail", Map.<String, Object>of(
                                "opsclear.mail.from", "no-reply@opsclear.test",
                                "opsclear.mail.from-name", "OpsClear",
                                "opsclear.app.base-url", "http://app.test",
                                "opsclear.mail.reply-to", replyTo))));
    }

    private RequestPostProcessor ownerJwt() {
        return jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner"));
    }

    private void createInvite(String email, String language) throws Exception {
        mockMvc.perform(post(ApiPaths.orgInvites(orgId))
                        .with(ownerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "language", language))))
                .andExpect(status().isCreated());
    }

    // The send runs after commit on the notification executor, so wait for it
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
