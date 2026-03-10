package com.opsclear.service;

import com.opsclear.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler — generic catch-all")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleGeneric_shouldReturn500_withGenericMessageAndNoInternalDetails")
    void handleGeneric_shouldReturn500_withGenericMessageAndNoInternalDetails() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneric(new RuntimeException("unexpected db failure"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .containsEntry("error", "Internal Server Error")
                .containsEntry("message", "An unexpected error occurred")
                .containsKey("timestamp")
                .doesNotContainKey("trace")
                .doesNotContainKey("stackTrace")
                .doesNotContainValue("unexpected db failure");
    }
}
