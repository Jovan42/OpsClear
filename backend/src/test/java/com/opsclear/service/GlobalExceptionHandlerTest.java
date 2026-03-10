package com.opsclear.service;

import com.opsclear.exception.ErrorResponse;
import com.opsclear.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler — generic catch-all")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleGeneric_shouldReturn500_withGenericMessageAndNoInternalDetails")
    void handleGeneric_shouldReturn500_withGenericMessageAndNoInternalDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("unexpected db failure"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().timestamp()).isNotBlank();
        assertThat(response.getBody().message()).doesNotContain("unexpected db failure");
    }
}
