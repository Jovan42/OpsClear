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
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-24T10:15:00Z");

    @Mock private JavaMailSender mailSender;

    private EmailService emailService;
    private UUID inviteId;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "no-reply@opsclear.test", "OpsClear", "https://app.example.com");
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
                .contains("The invite expires on November 24, 2026, 10:15 UTC.");
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
        emailService = new EmailService(mailSender, "no-reply@opsclear.test", "OpsClear", "http://localhost:5173/");

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
                .doesNotContain("<b>Evil</b>");
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

    // ─── helpers ─────────────────────────────────────────────────────────────

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
