package com.opsclear.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SignupCode and SignupList")
class SignupCodeTest {

    @Test
    @DisplayName("parse_shouldTrimAndUpperCase_aKnownCode")
    void parse_shouldTrimAndUpperCase_aKnownCode() {
        assertThat(SignupCode.parse("TRIAL")).contains(SignupCode.TRIAL);
        assertThat(SignupCode.parse("  trial ")).contains(SignupCode.TRIAL);
        assertThat(SignupCode.parse("Trial")).contains(SignupCode.TRIAL);
    }

    @Test
    @DisplayName("parse_shouldReturnEmpty_forUnknownBlankOrMissingCode")
    void parse_shouldReturnEmpty_forUnknownBlankOrMissingCode() {
        assertThat(SignupCode.parse("FREE100")).isEmpty();
        assertThat(SignupCode.parse("")).isEmpty();
        assertThat(SignupCode.parse("   ")).isEmpty();
        assertThat(SignupCode.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("trialDays_shouldCarryTheTotalDays")
    void trialDays_shouldCarryTheTotalDays() {
        assertThat(new TrialDays(30).totalDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("fromValue_shouldMatchTheExactWireValue")
    void fromValue_shouldMatchTheExactWireValue() {
        assertThat(SignupList.fromValue("early-access")).contains(SignupList.EARLY_ACCESS);
        assertThat(SignupList.EARLY_ACCESS.getValue()).isEqualTo("early-access");
        assertThat(SignupList.fromValue("Early-Access")).isEmpty();
        assertThat(SignupList.fromValue("EARLY_ACCESS")).isEmpty();
        assertThat(SignupList.fromValue(null)).isEmpty();
    }
}
