package com.opsclear.service;

import com.opsclear.dto.EmailSignupRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.EmailSignupModel;
import com.opsclear.model.SignupCode;
import com.opsclear.model.SignupList;
import com.opsclear.repository.EmailSignupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Email sign-up lists (ADR-0066): the public sign-up and the super-admin list, CSV export and delete.
 *
 * <p>The email address is personal data and is never logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSignupService {

    static final int UTM_MAX_LENGTH = 100;
    static final String CSV_HEADER = "id,list,email,code,redeemed_at,redeemed_org_id,locale,"
            + "utm_source,utm_medium,utm_campaign,consent_at,created_at";
    private static final String CSV_LINE_END = "\r\n";
    // A cell starting with one of these is run as a formula by spreadsheets (CSV injection)
    private static final Pattern FORMULA_START = Pattern.compile("^[=+\\-@]");
    // RFC 4180: a field with a comma, quote or line break must be quoted
    private static final Pattern NEEDS_QUOTES = Pattern.compile("[\",\r\n]");

    private final EmailSignupRepository emailSignupRepository;

    /**
     * Stores the sign-up, or quietly accepts a duplicate (only an empty code is filled in). A filled honeypot is
     * accepted and nothing is stored, so bots get the same answer as people.
     */
    @Transactional
    public void signUp(EmailSignupRequest request) {
        if (StringUtils.hasText(request.getWebsite())) {
            log.info("Email sign-up ignored: honeypot filled");
            return;
        }
        SignupList list = requireList(request.getList());
        String code = requireKnownCode(request.getCode());

        EmailSignupModel signup = EmailSignupModel.builder()
                .list(list.getValue())
                .email(request.getEmail().trim())
                .code(code)
                .locale(request.getLocale())
                .utmSource(truncateUtm(request.getUtmSource()))
                .utmMedium(truncateUtm(request.getUtmMedium()))
                .utmCampaign(truncateUtm(request.getUtmCampaign()))
                .build();

        if (emailSignupRepository.upsert(signup)) {
            log.info("Email sign-up stored on list {}", list.getValue());
        } else {
            log.info("Email sign-up duplicate on list {}", list.getValue());
        }
    }

    /** The sign-ups on the list, newest first. */
    @Transactional(readOnly = true)
    public List<EmailSignupModel> list(String list) {
        return emailSignupRepository.findByListOrderByCreatedAtDesc(requireList(list).getValue());
    }

    /** The list as CSV: a header row, then one row per sign-up, newest first. */
    @Transactional(readOnly = true)
    public String exportCsv(String list) {
        StringBuilder csv = new StringBuilder(CSV_HEADER).append(CSV_LINE_END);
        for (EmailSignupModel signup : list(list)) {
            csv.append(toCsvRow(signup)).append(CSV_LINE_END);
        }
        return csv.toString();
    }

    @Transactional
    public void delete(UUID id) {
        if (!emailSignupRepository.deleteById(id)) {
            throw new NotFoundException(ErrorMessages.EmailSignup.NOT_FOUND);
        }
        log.info("Deleted email sign-up {}", id);
    }

    static String truncateUtm(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), UTM_MAX_LENGTH));
    }

    static String csvCell(Object value) {
        String text = Objects.toString(value, "");
        if (FORMULA_START.matcher(text).find()) {
            text = "'" + text;
        }
        if (NEEDS_QUOTES.matcher(text).find()) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String toCsvRow(EmailSignupModel s) {
        return Stream.of(s.getId(), s.getList(), s.getEmail(), s.getCode(), s.getRedeemedAt(), s.getRedeemedOrgId(),
                        s.getLocale(), s.getUtmSource(), s.getUtmMedium(), s.getUtmCampaign(), s.getConsentAt(),
                        s.getCreatedAt())
                .map(EmailSignupService::csvCell)
                .collect(Collectors.joining(","));
    }

    // --- Guards ---

    private SignupList requireList(String list) {
        return SignupList.fromValue(list)
                .orElseThrow(() -> new BadRequestException(ErrorMessages.EmailSignup.UNKNOWN_LIST));
    }

    /** The upper-case code, or null when none was given. */
    private String requireKnownCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return SignupCode.parse(code)
                .map(SignupCode::name)
                .orElseThrow(() -> new BadRequestException(ErrorMessages.EmailSignup.UNKNOWN_CODE));
    }
}
