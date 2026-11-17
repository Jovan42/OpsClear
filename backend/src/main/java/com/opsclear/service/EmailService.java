package com.opsclear.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/**
 * Sends transactional email from the backend over SMTP (ADR-0059): Mailpit in dev, Brevo on prod. Texts come from
 * the {@code mail/messages_*.properties} bundles (en, sr) and are put into a plain-text and a simple HTML part.
 * No template engine.
 *
 * <p>Sending is best-effort: a failure is logged at WARN with the invite ID only and never thrown, because the
 * invite is already saved and owners/admins can always copy the link instead. The recipient's address and the
 * token are never logged, and neither is the exception message, since SMTP errors can contain the address.
 */
@Service
@Slf4j
public class EmailService {

    static final String SERBIAN = "sr";
    static final String BUNDLE = "mail/messages";

    private static final Locale EN = Locale.ENGLISH;
    private static final Locale SR = Locale.forLanguageTag("sr-Latn");
    private static final DateTimeFormatter EN_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", EN);
    private static final DateTimeFormatter SR_DATE = DateTimeFormatter.ofPattern("d.M.yyyy.", SR);

    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final String from;
    private final String fromName;
    private final String baseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${opsclear.mail.from}") String from,
            @Value("${opsclear.mail.from-name}") String fromName,
            @Value("${opsclear.app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.messages = mailMessages();
        this.from = from;
        this.fromName = fromName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void sendInvite(InviteEmail email) {
        try {
            boolean serbian = SERBIAN.equals(email.language());
            Locale locale = serbian ? SR : EN;
            String link = baseUrl + "/invite/" + email.token();
            String expiry = (serbian ? SR_DATE : EN_DATE).format(email.expiresAt().atZone(ZoneOffset.UTC));

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(email.to());
            helper.setSubject(text("invite.subject", locale, email.orgName()));
            helper.setText(
                    inviteText(locale, email.inviterName(), email.orgName(), link, expiry),
                    inviteHtml(locale, email.inviterName(), email.orgName(), link, expiry));
            mailSender.send(message);
            log.info("Invite email sent for invite {}", email.inviteId());
        } catch (Exception e) {
            log.warn("Invite email for invite {} could not be sent ({})", email.inviteId(),
                    e.getClass().getSimpleName());
        }
    }

    private String inviteText(Locale locale, String inviter, String org, String link, String expiry) {
        return String.join("\n\n",
                text("invite.greeting", locale),
                text("invite.body", locale, inviter, org),
                text("invite.linkIntro", locale) + "\n" + link,
                text("invite.expiry", locale, expiry),
                text("invite.ignore", locale)) + "\n";
    }

    private String inviteHtml(Locale locale, String inviter, String org, String link, String expiry) {
        String href = HtmlUtils.htmlEscape(link);
        return "<!DOCTYPE html><html><body style=\"font-family: Arial, sans-serif; color: #1f2937;\">"
                + "<p>" + html("invite.greeting", locale) + "</p>"
                + "<p>" + html("invite.body", locale, inviter, org) + "</p>"
                + "<p><a href=\"" + href + "\" style=\"display: inline-block; padding: 10px 18px;"
                + " background: #2563eb; color: #ffffff; text-decoration: none; border-radius: 6px;\">"
                + html("invite.action", locale) + "</a></p>"
                + "<p>" + html("invite.linkIntro", locale) + "<br><a href=\"" + href + "\">" + href + "</a></p>"
                + "<p>" + html("invite.expiry", locale, expiry) + "</p>"
                + "<p style=\"color: #6b7280; font-size: 12px;\">" + html("invite.ignore", locale) + "</p>"
                + "</body></html>";
    }

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    // The bundle texts are ours and contain no markup; the arguments (org and inviter names) are user input.
    private String html(String key, Locale locale, String... args) {
        Object[] escaped = Arrays.stream(args).map(HtmlUtils::htmlEscape).toArray();
        return messages.getMessage(key, escaped, locale);
    }

    private static MessageSource mailMessages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename(BUNDLE);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        // Apostrophes are always written as '' in the bundles, with or without arguments.
        source.setAlwaysUseMessageFormat(true);
        return source;
    }
}
