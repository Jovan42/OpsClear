package com.opsclear.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmailService")
class EmailServiceTest {

    private final EmailService emailService = new EmailService();

    @Test
    @DisplayName("sendInvite_shouldLogWithoutThrowing")
    void sendInvite_shouldLogWithoutThrowing() {
        emailService.sendInvite("user@example.com", "abc123token");
    }
}
