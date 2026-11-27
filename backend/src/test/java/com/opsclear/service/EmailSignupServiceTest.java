package com.opsclear.service;

import com.opsclear.dto.EmailSignupRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.EmailSignupModel;
import com.opsclear.repository.EmailSignupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailSignupService")
class EmailSignupServiceTest {

    @Mock private EmailSignupRepository emailSignupRepository;

    private EmailSignupService service;

    @BeforeEach
    void setUp() {
        service = new EmailSignupService(emailSignupRepository);
    }

    private static EmailSignupRequest.EmailSignupRequestBuilder validRequest() {
        return EmailSignupRequest.builder().list("early-access").email("Ana@Example.com").consent(true);
    }

    private EmailSignupModel captureUpsert() {
        ArgumentCaptor<EmailSignupModel> captor = ArgumentCaptor.forClass(EmailSignupModel.class);
        verify(emailSignupRepository).upsert(captor.capture());
        return captor.getValue();
    }

    // --- signUp ---

    @Test
    @DisplayName("signUp_shouldStoreTrimmedEmailInItsCase_withListLocaleUtmAndUpperCaseCode")
    void signUp_shouldStoreTrimmedEmailInItsCase_withListLocaleUtmAndUpperCaseCode() {
        when(emailSignupRepository.upsert(any())).thenReturn(true);

        service.signUp(validRequest().email("  Ana@Example.com ").locale("sr").utmSource("linkedin")
                .utmMedium("social").utmCampaign("launch").code(" trial ").website("").build());

        EmailSignupModel stored = captureUpsert();
        assertThat(stored.getList()).isEqualTo("early-access");
        assertThat(stored.getEmail()).isEqualTo("Ana@Example.com");
        assertThat(stored.getLocale()).isEqualTo("sr");
        assertThat(stored.getUtmSource()).isEqualTo("linkedin");
        assertThat(stored.getUtmMedium()).isEqualTo("social");
        assertThat(stored.getUtmCampaign()).isEqualTo("launch");
        assertThat(stored.getCode()).isEqualTo("TRIAL");
    }

    @Test
    @DisplayName("signUp_shouldStoreNoCode_whenCodeIsMissingOrBlank")
    void signUp_shouldStoreNoCode_whenCodeIsMissingOrBlank() {
        when(emailSignupRepository.upsert(any())).thenReturn(false);

        service.signUp(validRequest().code("   ").build());

        EmailSignupModel stored = captureUpsert();
        assertThat(stored.getCode()).isNull();
        assertThat(stored.getUtmSource()).isNull();
        assertThat(stored.getLocale()).isNull();
    }

    @Test
    @DisplayName("signUp_shouldStoreNothing_whenHoneypotIsFilled")
    void signUp_shouldStoreNothing_whenHoneypotIsFilled() {
        service.signUp(validRequest().list("no-such-list").website("http://spam.example").build());

        verifyNoInteractions(emailSignupRepository);
    }

    @Test
    @DisplayName("signUp_shouldThrowBadRequest_whenListIsUnknown")
    void signUp_shouldThrowBadRequest_whenListIsUnknown() {
        assertThatThrownBy(() -> service.signUp(validRequest().list("newsletter").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown sign-up list");

        verify(emailSignupRepository, never()).upsert(any());
    }

    @Test
    @DisplayName("signUp_shouldThrowBadRequest_whenCodeIsUnknown")
    void signUp_shouldThrowBadRequest_whenCodeIsUnknown() {
        assertThatThrownBy(() -> service.signUp(validRequest().code("FREE100").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This code isn't valid");

        verify(emailSignupRepository, never()).upsert(any());
    }

    @Test
    @DisplayName("request_shouldTrimTheEmailWhenBound_andKeepItsCase")
    void request_shouldTrimTheEmailWhenBound_andKeepItsCase() {
        EmailSignupRequest request = new EmailSignupRequest();

        request.setEmail(" \tAna@Example.com  ");
        assertThat(request.getEmail()).isEqualTo("Ana@Example.com");

        request.setEmail(null);
        assertThat(request.getEmail()).isNull();
    }

    // --- truncateUtm ---

    @Test
    @DisplayName("truncateUtm_shouldCutTo100Characters_andKeepShorterValuesAndNull")
    void truncateUtm_shouldCutTo100Characters_andKeepShorterValuesAndNull() {
        assertThat(EmailSignupService.truncateUtm("x".repeat(150))).isEqualTo("x".repeat(100));
        assertThat(EmailSignupService.truncateUtm("x".repeat(100))).isEqualTo("x".repeat(100));
        assertThat(EmailSignupService.truncateUtm("linkedin")).isEqualTo("linkedin");
        assertThat(EmailSignupService.truncateUtm(null)).isNull();
    }

    // --- list ---

    @Test
    @DisplayName("list_shouldReturnTheListsSignups")
    void list_shouldReturnTheListsSignups() {
        EmailSignupModel signup = EmailSignupModel.builder().id(UUID.randomUUID()).build();
        when(emailSignupRepository.findByListOrderByCreatedAtDesc("early-access")).thenReturn(List.of(signup));

        assertThat(service.list("early-access")).containsExactly(signup);
    }

    @Test
    @DisplayName("list_shouldThrowBadRequest_whenListIsUnknownOrMissing")
    void list_shouldThrowBadRequest_whenListIsUnknownOrMissing() {
        assertThatThrownBy(() -> service.list("newsletter")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(emailSignupRepository);
    }

    // --- exportCsv ---

    @Test
    @DisplayName("exportCsv_shouldWriteHeaderAndOneRowPerSignup_withCrlfAndEmptyCellsForNulls")
    void exportCsv_shouldWriteHeaderAndOneRowPerSignup_withCrlfAndEmptyCellsForNulls() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        EmailSignupModel full = EmailSignupModel.builder()
                .id(id).list("early-access").email("ana@example.com").code("TRIAL")
                .redeemedAt(Instant.parse("2026-12-01T10:00:00Z")).redeemedOrgId(orgId).locale("en")
                .utmSource("linkedin").utmMedium("social").utmCampaign("launch, \"q4\"")
                .consentAt(Instant.parse("2026-11-27T08:00:00Z")).createdAt(Instant.parse("2026-11-27T08:00:00Z"))
                .build();
        EmailSignupModel bare = EmailSignupModel.builder()
                .id(id).list("early-access").email("bob@example.com")
                .consentAt(Instant.parse("2026-11-26T08:00:00Z")).createdAt(Instant.parse("2026-11-26T08:00:00Z"))
                .build();
        when(emailSignupRepository.findByListOrderByCreatedAtDesc("early-access")).thenReturn(List.of(full, bare));

        String csv = service.exportCsv("early-access");

        assertThat(csv).isEqualTo(EmailSignupService.CSV_HEADER + "\r\n"
                + id + ",early-access,ana@example.com,TRIAL,2026-12-01T10:00:00Z," + orgId
                + ",en,linkedin,social,\"launch, \"\"q4\"\"\",2026-11-27T08:00:00Z,2026-11-27T08:00:00Z\r\n"
                + id + ",early-access,bob@example.com,,,,,,,,2026-11-26T08:00:00Z,2026-11-26T08:00:00Z\r\n");
    }

    @Test
    @DisplayName("exportCsv_shouldWriteOnlyTheHeader_whenListIsEmpty")
    void exportCsv_shouldWriteOnlyTheHeader_whenListIsEmpty() {
        when(emailSignupRepository.findByListOrderByCreatedAtDesc("early-access")).thenReturn(List.of());

        assertThat(service.exportCsv("early-access")).isEqualTo(EmailSignupService.CSV_HEADER + "\r\n");
    }

    // --- csvCell ---

    @Test
    @DisplayName("csvCell_shouldPrefixFormulaStartsWithAQuote")
    void csvCell_shouldPrefixFormulaStartsWithAQuote() {
        assertThat(EmailSignupService.csvCell("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(EmailSignupService.csvCell("+1")).isEqualTo("'+1");
        assertThat(EmailSignupService.csvCell("-1")).isEqualTo("'-1");
        assertThat(EmailSignupService.csvCell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(EmailSignupService.csvCell("a=b")).isEqualTo("a=b");
    }

    @Test
    @DisplayName("csvCell_shouldQuoteCommasQuotesAndLineBreaks_perRfc4180")
    void csvCell_shouldQuoteCommasQuotesAndLineBreaks_perRfc4180() {
        assertThat(EmailSignupService.csvCell("a,b")).isEqualTo("\"a,b\"");
        assertThat(EmailSignupService.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(EmailSignupService.csvCell("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(EmailSignupService.csvCell("a\rb")).isEqualTo("\"a\rb\"");
        assertThat(EmailSignupService.csvCell("=1,2")).isEqualTo("\"'=1,2\"");
        assertThat(EmailSignupService.csvCell("plain")).isEqualTo("plain");
    }

    @Test
    @DisplayName("csvCell_shouldWriteEmptyCell_forNullAndEmpty")
    void csvCell_shouldWriteEmptyCell_forNullAndEmpty() {
        assertThat(EmailSignupService.csvCell(null)).isEmpty();
        assertThat(EmailSignupService.csvCell("")).isEmpty();
    }

    // --- delete ---

    @Test
    @DisplayName("delete_shouldDeleteTheRow")
    void delete_shouldDeleteTheRow() {
        UUID id = UUID.randomUUID();
        when(emailSignupRepository.deleteById(id)).thenReturn(true);

        service.delete(id);

        verify(emailSignupRepository).deleteById(id);
    }

    @Test
    @DisplayName("delete_shouldThrowNotFound_whenRowIsMissing")
    void delete_shouldThrowNotFound_whenRowIsMissing() {
        UUID id = UUID.randomUUID();
        when(emailSignupRepository.deleteById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Sign-up not found");
    }
}
