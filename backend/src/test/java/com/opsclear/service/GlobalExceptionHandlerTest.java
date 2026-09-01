package com.opsclear.service;

import com.opsclear.exception.ErrorResponse;
import com.opsclear.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @Test
    @DisplayName("handleNoResourceFound_shouldReturn404_forAnUnmappedRoute (JOB-253)")
    void handleNoResourceFound_shouldReturn404_forAnUnmappedRoute() {
        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.DELETE, "api/projects/x/jobs/y/notes/z"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("handleMalformedRequest_shouldReturn400_forUnreadableRequestBody")
    void handleMalformedRequest_shouldReturn400_forUnreadableRequestBody() {
        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequest(
                new HttpMessageNotReadableException("Cannot deserialize value of type `JobTypeColor`", (HttpInputMessage) null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Malformed request body");
    }
}
