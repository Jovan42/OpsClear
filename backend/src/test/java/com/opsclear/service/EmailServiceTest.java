package com.opsclear.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("EmailService")
class EmailServiceTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String TO = "invitee@example.com";
    private static final String SUPPORT = "support@opsclear.test";
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-24T10:15:00Z");

    @Mock private JavaMailSender mailSender;

    private EmailService emailService;
    private UUID inviteId;

    @BeforeEach
    void setUp() {
        emailService = service("https://app.example.com", "");
        inviteId = UUID.randomUUID();
    }

    // ─── sendInvite ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendInvite_shouldSendEnglishEmail_whenLanguageIsEn")
    void sendInvite_shouldSendEnglishEmail_whenLanguageIsEn() throws Exception {
        MimeMessage sent = send(invite("en", "Acme Corp", "Olivia Owner"));

        assertThat(sent.getSubject()).isEqualTo("You're invited to join Acme Corp on OpsClear");
        assertThat(sent.getAllRecipients()).extracting(Object::toString).containsExactly(TO);
        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@opsclear.test");
        assertThat(from.getPersonal()).isEqualTo("OpsClear");

        String text = textPart(sent, "text/plain");
        assertThat(text)
                .contains("Olivia Owner has invited you to join Acme Corp on OpsClear.")
                .contains("https://app.example.com/invite/" + TOKEN)
                .contains("The invite expires on November 24, 2026, 10:15 UTC.")
                .contains("If you weren't expecting this invite, you can ignore this email.");

        String html = textPart(sent, "text/html");
        assertThat(html)
                .contains("Olivia Owner has invited you to join Acme Corp on OpsClear.")
                .contains("href=\"https://app.example.com/invite/" + TOKEN + "\"")
                .contains("Accept invite")
                .contains("If the button doesn't work, copy this link into your browser:")
                .contains("The invite expires on November 24, 2026, 10:15 UTC.");
        assertThat(text).doesNotContain("If the button doesn't work");
    }

    @Test
    @DisplayName("sendInvite_shouldUseBrandedLayout_whenBuildingHtml")
    void sendInvite_shouldUseBrandedLayout_whenBuildingHtml() throws Exception {
        String html = textPart(send(invite("en", "Acme Corp", "Olivia Owner")), "text/html");

        assertThat(html)
                // light grey background and centred white card
                .contains("<body style=\"margin:0; padding:0; background-color:#f3f4f6;\">")
                .contains("max-width:560px; background-color:#ffffff; border:1px solid #e5e7eb; border-radius:12px;")
                // green header, body text in the Keycloak font stack
                .contains("font-size:18px; font-weight:700; color:#059669;\">OpsClear</td>")
                .contains("font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,"
                        + "sans-serif; font-size:15px; line-height:1.6; color:#374151;")
                // brand button
                .contains("<a href=\"https://app.example.com/invite/" + TOKEN + "\" style=\"display:inline-block;"
                        + " background-color:#059669; color:#ffffff; font-weight:600; text-decoration:none;"
                        + " padding:12px 24px; border-radius:8px;\">Accept invite</a>")
                // raw-link fallback that wraps on phones
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0 0 16px;\">If the button doesn't work,"
                        + " copy this link into your browser:<br><span style=\"word-break:break-all;\">"
                        + "https://app.example.com/invite/" + TOKEN + "</span></p>")
                // expiry and ignore lines in small grey text
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0 0 16px;\">The invite expires on")
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0;\">If you weren't expecting")
                .doesNotContain("<style")
                .doesNotContain("<img")
                .doesNotContain("#2563eb");
    }

    @Test
    @DisplayName("sendInvite_shouldSendSerbianEmail_whenLanguageIsSr")
    void sendInvite_shouldSendSerbianEmail_whenLanguageIsSr() throws Exception {
        MimeMessage sent = send(invite("sr", "Acme Corp", "Olivia Owner"));

        assertThat(sent.getSubject()).isEqualTo("Pozvani ste da se pridružite organizaciji Acme Corp na OpsClear-u");

        String text = textPart(sent, "text/plain");
        assertThat(text)
                .contains("Olivia Owner vas poziva da se pridružite organizaciji Acme Corp na OpsClear-u.")
                .contains("https://app.example.com/invite/" + TOKEN)
                .contains("Pozivnica ističe 24.11.2026. u 10:15 UTC.")
                .contains("Ako niste očekivali ovu pozivnicu, možete zanemariti ovaj email.");

        String html = textPart(sent, "text/html");
        assertThat(html)
                .contains("Prihvati pozivnicu")
                .contains("Ako dugme ne radi, kopirajte ovaj link u pregledač:")
                .contains("href=\"https://app.example.com/invite/" + TOKEN + "\"");
    }

    @Test
    @DisplayName("sendInvite_shouldFallBackToEnglish_whenLanguageIsMissing")
    void sendInvite_shouldFallBackToEnglish_whenLanguageIsMissing() throws Exception {
        MimeMessage sent = send(invite(null, "Acme Corp", "Olivia Owner"));

        assertThat(sent.getSubject()).isEqualTo("You're invited to join Acme Corp on OpsClear");
    }

    @Test
    @DisplayName("sendInvite_shouldBuildLinkWithoutDoubleSlash_whenBaseUrlEndsWithSlash")
    void sendInvite_shouldBuildLinkWithoutDoubleSlash_whenBaseUrlEndsWithSlash() throws Exception {
        emailService = service("http://localhost:5173/", "");

        MimeMessage sent = send(invite("en", "Acme Corp", "Olivia Owner"));

        assertThat(textPart(sent, "text/plain")).contains("http://localhost:5173/invite/" + TOKEN)
                .doesNotContain("5173//invite");
    }

    @Test
    @DisplayName("sendInvite_shouldEscapeNamesInHtml_whenNamesContainMarkup")
    void sendInvite_shouldEscapeNamesInHtml_whenNamesContainMarkup() throws Exception {
        MimeMessage sent = send(invite("en", "<b>Evil</b> & Co", "Mallory <script>"));

        String html = textPart(sent, "text/html");
        assertThat(html)
                .contains("Mallory &lt;script&gt; has invited you to join &lt;b&gt;Evil&lt;/b&gt; &amp; Co on OpsClear.")
                .doesNotContain("<script>")
                .doesNotContain("<b>Evil</b>")
                .contains("background-color:#059669")
                .contains("word-break:break-all;");
        assertThat(textPart(sent, "text/plain")).contains("Mallory <script> has invited you to join <b>Evil</b> & Co");
    }

    @Test
    @DisplayName("sendInvite_shouldSwallowAndLogWithoutEmailOrToken_whenSendFails")
    void sendInvite_shouldSwallowAndLogWithoutEmailOrToken_whenSendFails(CapturedOutput output) {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("550 rejected recipient " + TO + " token " + TOKEN))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> emailService.sendInvite(invite("en", "Acme Corp", "Olivia Owner")))
                .doesNotThrowAnyException();

        assertThat(output.getOut())
                .contains("WARN")
                .contains("Invite email for invite " + inviteId + " could not be sent (MailSendException)")
                .doesNotContain(TO)
                .doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("sendInvite_shouldSwallowAndLog_whenMessageCannotBeBuilt")
    void sendInvite_shouldSwallowAndLog_whenMessageCannotBeBuilt(CapturedOutput output) {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        InviteEmail noAddress = new InviteEmail(inviteId, null, "en", "Acme", "Olivia", TOKEN, EXPIRES_AT);

        assertThatCode(() -> emailService.sendInvite(noAddress)).doesNotThrowAnyException();

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(output.getOut())
                .contains("Invite email for invite " + inviteId + " could not be sent (IllegalArgumentException)")
                .doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("sendInvite_shouldLogSuccessWithoutEmailOrToken_whenSent")
    void sendInvite_shouldLogSuccessWithoutEmailOrToken_whenSent(CapturedOutput output) throws Exception {
        send(invite("en", "Acme Corp", "Olivia Owner"));

        assertThat(output.getOut())
                .contains("Invite email sent for invite " + inviteId)
                .doesNotContain(TO)
                .doesNotContain(TOKEN);
    }

    // ─── Reply-To and support footer (ADR-0064) ───────────────────────────────

    @Test
    @DisplayName("sendInvite_shouldSetReplyTo_whenReplyToIsConfigured")
    void sendInvite_shouldSetReplyTo_whenReplyToIsConfigured() throws Exception {
        emailService = service("https://app.example.com", SUPPORT);

        MimeMessage sent = send(invite("en", "Acme Corp", "Olivia Owner"));

        assertThat(sent.getReplyTo()).extracting(Object::toString).containsExactly(SUPPORT);
        assertThat(sent.getHeader("Reply-To")).containsExactly(SUPPORT);
        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@opsclear.test");
    }

    @Test
    @DisplayName("sendInvite_shouldTrimReplyTo_whenConfiguredWithSurroundingSpaces")
    void sendInvite_shouldTrimReplyTo_whenConfiguredWithSurroundingSpaces() throws Exception {
        emailService = service("https://app.example.com", "  " + SUPPORT + " ");

        MimeMessage sent = send(invite("en", "Acme Corp", "Olivia Owner"));

        assertThat(sent.getHeader("Reply-To")).containsExactly(SUPPORT);
        assertThat(textPart(sent, "text/plain")).contains("write to " + SUPPORT + "\n");
    }

    @Test
    @DisplayName("sendInvite_shouldAddEnglishSupportFooter_whenReplyToIsConfigured")
    void sendInvite_shouldAddEnglishSupportFooter_whenReplyToIsConfigured() throws Exception {
        emailService = service("https://app.example.com", SUPPORT);

        MimeMessage sent = send(invite("en", "Acme Corp", "Olivia Owner"));

        assertThat(textPart(sent, "text/plain")).endsWith(
                "If you weren't expecting this invite, you can ignore this email.\n\n"
                        + "Questions? Reply to this email or write to " + SUPPORT + "\n");
        String html = textPart(sent, "text/html");
        assertThat(html)
                // the ignore line is no longer the last paragraph, so it keeps its bottom margin
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0 0 16px;\">If you weren't expecting")
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0;\">Questions? Reply to this email or"
                        + " write to <a href=\"mailto:" + SUPPORT + "\" style=\"color:#059669;\">" + SUPPORT
                        + "</a></p>\n</td>");
        assertThat(html.indexOf("If you weren't expecting")).isLessThan(html.indexOf("Questions?"));
    }

    @Test
    @DisplayName("sendInvite_shouldAddSerbianSupportFooter_whenReplyToIsConfigured")
    void sendInvite_shouldAddSerbianSupportFooter_whenReplyToIsConfigured() throws Exception {
        emailService = service("https://app.example.com", SUPPORT);

        MimeMessage sent = send(invite("sr", "Acme Corp", "Olivia Owner"));

        assertThat(textPart(sent, "text/plain"))
                .endsWith("\n\nImate pitanje? Odgovorite na ovaj mejl ili pišite na " + SUPPORT + "\n");
        assertThat(textPart(sent, "text/html"))
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0;\">Imate pitanje? Odgovorite na ovaj"
                        + " mejl ili pišite na <a href=\"mailto:" + SUPPORT + "\" style=\"color:#059669;\">"
                        + SUPPORT + "</a></p>")
                .doesNotContain("Questions?");
    }

    @Test
    @DisplayName("sendInvite_shouldEscapeReplyToInHtml_whenAddressContainsMarkup")
    void sendInvite_shouldEscapeReplyToInHtml_whenAddressContainsMarkup() throws Exception {
        emailService = service("https://app.example.com", "a&b@opsclear.test");

        String html = textPart(send(invite("en", "Acme Corp", "Olivia Owner")), "text/html");

        assertThat(html).contains("<a href=\"mailto:a&amp;b@opsclear.test\" style=\"color:#059669;\">"
                + "a&amp;b@opsclear.test</a>");
    }

    @Test
    @DisplayName("sendInvite_shouldSendNoReplyToAndNoFooter_whenReplyToIsBlank")
    void sendInvite_shouldSendNoReplyToAndNoFooter_whenReplyToIsBlank() throws Exception {
        emailService = service("https://app.example.com", "   ");

        assertNoReplyToAndNoFooter(send(invite("en", "Acme Corp", "Olivia Owner")));
    }

    @Test
    @DisplayName("sendInvite_shouldSendNoReplyToAndNoFooter_whenReplyToIsEmpty")
    void sendInvite_shouldSendNoReplyToAndNoFooter_whenReplyToIsEmpty() throws Exception {
        assertNoReplyToAndNoFooter(send(invite("sr", "Acme Corp", "Olivia Owner")));
    }

    @Test
    @DisplayName("sendInvite_shouldSendNoReplyToAndNoFooter_whenReplyToIsNull")
    void sendInvite_shouldSendNoReplyToAndNoFooter_whenReplyToIsNull() throws Exception {
        emailService = service("https://app.example.com", null);

        assertNoReplyToAndNoFooter(send(invite("en", "Acme Corp", "Olivia Owner")));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private EmailService service(String baseUrl, String replyTo) {
        return new EmailService(mailSender, "no-reply@opsclear.test", "OpsClear", baseUrl, replyTo);
    }

    private static void assertNoReplyToAndNoFooter(MimeMessage sent) throws Exception {
        assertThat(sent.getHeader("Reply-To")).isNull();
        String text = textPart(sent, "text/plain");
        String html = textPart(sent, "text/html");
        assertThat(text).doesNotContain("Questions?").doesNotContain("Imate pitanje?").doesNotContain("mailto:");
        assertThat(html).doesNotContain("Questions?").doesNotContain("Imate pitanje?").doesNotContain("mailto:")
                // the ignore line stays the last paragraph, without a bottom margin
                .contains("<p style=\"font-size:13px; color:#6b7280; margin:0;\">");
    }

    private InviteEmail invite(String language, String orgName, String inviterName) {
        return new InviteEmail(inviteId, TO, language, orgName, inviterName, TOKEN, EXPIRES_AT);
    }

    private MimeMessage send(InviteEmail email) throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        emailService.sendInvite(email);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return message;
    }

    private static String textPart(MimeMessage message, String mimeType) throws Exception {
        List<String> found = new ArrayList<>();
        collect(message, mimeType, found);
        assertThat(found).as("%s parts", mimeType).hasSize(1);
        return found.get(0);
    }

    private static void collect(Part part, String mimeType, List<String> found) throws Exception {
        if (part.isMimeType(mimeType)) {
            found.add(content(part));
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, mimeType, found);
            }
        }
    }

    private static String content(Part part) throws IOException, jakarta.mail.MessagingException {
        return (String) part.getContent();
    }
}
