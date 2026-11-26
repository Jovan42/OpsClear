package com.opsclear.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/**
 * Sends transactional email from the backend over SMTP (ADR-0059): Mailpit in dev, Brevo on prod. Texts come from
 * the {@code mail/messages_*.properties} bundles (en, sr) and are put into a plain-text part and an HTML part with the
 * same light layout as the Keycloak emails.
 * No template engine.
 *
 * <p>When {@code opsclear.mail.reply-to} is set (ADR-0064), replies go to that address and every email ends with a
 * support footer naming it. When it's blank (dev, tests, CI), there's no Reply-To header and no footer. A set but
 * malformed address stops the application at startup.
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
    // The invitee's time zone is unknown, so the expiry shows date and time with an explicit UTC
    private static final DateTimeFormatter EN_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy, HH:mm 'UTC'", EN);
    private static final DateTimeFormatter SR_DATE = DateTimeFormatter.ofPattern("d.M.yyyy. 'u' HH:mm 'UTC'", SR);

    private static final String FONT = "font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,"
            + "Helvetica,Arial,sans-serif;";
    private static final String SMALL_GREY = "font-size:13px; color:#6b7280;";
    private static final String HTML_START = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n</head>\n"
            + "<body style=\"margin:0; padding:0; background-color:#f3f4f6;\">\n"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
            + " style=\"background-color:#f3f4f6;\">\n<tr>\n<td align=\"center\" style=\"padding:32px 16px;\">\n"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
            + " style=\"max-width:560px; background-color:#ffffff; border:1px solid #e5e7eb; border-radius:12px;\">\n"
            + "<tr>\n<td style=\"padding:24px 32px 0; " + FONT
            + " font-size:18px; font-weight:700; color:#059669;\">OpsClear</td>\n</tr>\n"
            + "<tr>\n<td style=\"padding:8px 32px 32px; " + FONT
            + " font-size:15px; line-height:1.6; color:#374151;\">\n";
    private static final String HTML_END = "\n</td>\n</tr>\n</table>\n</td>\n</tr>\n</table>\n</body>\n</html>\n";

    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final String from;
    private final String fromName;
    private final String baseUrl;
    private final String replyTo;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${opsclear.mail.from}") String from,
            @Value("${opsclear.mail.from-name}") String fromName,
            @Value("${opsclear.app.base-url}") String baseUrl,
            @Value("${opsclear.mail.reply-to:}") String replyTo) {
        this.mailSender = mailSender;
        this.messages = mailMessages();
        this.from = from;
        this.fromName = fromName;
        this.baseUrl = baseUrl;
        this.replyTo = replyTo.strip();
        if (hasReplyTo()) {
            requireValidAddress(this.replyTo);
        }
    }

    // A typo in SMTP_REPLY_TO would otherwise make every send fail, and sends are best-effort (only a WARN each),
    // so fail at startup instead
    private static void requireValidAddress(String address) {
        try {
            new InternetAddress(address, true);
        } catch (AddressException e) {
            throw new IllegalStateException("opsclear.mail.reply-to (SMTP_REPLY_TO) is not a valid email address", e);
        }
    }

    public void sendInvite(InviteEmail email) {
        try {
            boolean serbian = SERBIAN.equals(email.language());
            Locale locale = serbian ? SR : EN;
            // pathSegment joins with exactly one slash, whether or not the base URL ends with one
            String link = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("invite", email.token())
                    .build().toUriString();
            String expiry = (serbian ? SR_DATE : EN_DATE).format(email.expiresAt().atZone(ZoneOffset.UTC));

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            if (hasReplyTo()) {
                // Same display name as From, like Keycloak's replyToDisplayName
                helper.setReplyTo(replyTo, fromName);
            }
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
                text("invite.ignore", locale))
                + (hasReplyTo() ? "\n\n" + text("footer.support", locale, replyTo) : "") + "\n";
    }

    // Same light layout, colours, spacing and font stack as the Keycloak emails (ADR-0059, amended 2026-11-17):
    // keycloak/themes/opsclear/email/html/template.ftl and email-verification.ftl. Inline styles and tables only,
    // because email clients ignore <style> blocks.
    private String inviteHtml(Locale locale, String inviter, String org, String link, String expiry) {
        String href = HtmlUtils.htmlEscape(link);
        return HTML_START
                + "<p style=\"margin:0 0 16px;\">" + html("invite.greeting", locale) + "</p>"
                + "<p style=\"margin:0 0 24px;\">" + html("invite.body", locale, inviter, org) + "</p>"
                + "<p style=\"margin:0 0 24px;\"><a href=\"" + href + "\" style=\"display:inline-block;"
                + " background-color:#059669; color:#ffffff; font-weight:600; text-decoration:none; padding:12px 24px;"
                + " border-radius:8px;\">" + html("invite.action", locale) + "</a></p>"
                + "<p style=\"" + SMALL_GREY + " margin:0 0 16px;\">" + html("invite.linkFallback", locale)
                + "<br><span style=\"word-break:break-all;\">" + href + "</span></p>"
                + "<p style=\"" + SMALL_GREY + " margin:0 0 16px;\">" + html("invite.expiry", locale, expiry) + "</p>"
                + "<p style=\"" + SMALL_GREY + " margin:" + (hasReplyTo() ? "0 0 16px" : "0") + ";\">"
                + html("invite.ignore", locale) + "</p>"
                + supportFooterHtml(locale)
                + HTML_END;
    }

    // Last small grey paragraph of the card, with the reply-to address as a mailto: link (ADR-0064)
    private String supportFooterHtml(Locale locale) {
        if (!hasReplyTo()) {
            return "";
        }
        String address = HtmlUtils.htmlEscape(replyTo);
        String mailto = "<a href=\"mailto:" + address + "\" style=\"color:#059669;\">" + address + "</a>";
        return "<p style=\"" + SMALL_GREY + " margin:0;\">"
                + messages.getMessage("footer.support", new Object[] {mailto}, locale) + "</p>";
    }

    private boolean hasReplyTo() {
        return !replyTo.isEmpty();
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
