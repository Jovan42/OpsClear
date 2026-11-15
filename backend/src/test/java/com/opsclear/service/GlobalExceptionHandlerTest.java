package com.opsclear.service;

import com.opsclear.exception.ErrorResponse;
import com.opsclear.exception.GlobalExceptionHandler;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("GlobalExceptionHandler — generic catch-all")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("handleGeneric_shouldReturn500_withGenericMessageAndNoInternalDetails")
    void handleGeneric_shouldReturn500_withGenericMessageAndNoInternalDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("unexpected db failure"), new MockHttpServletResponse());

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

    @Test
    @DisplayName("handleTypeMismatch_shouldReturn400_namingTheParameter")
    void handleTypeMismatch_shouldReturn400_namingTheParameter() {
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "weeks", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Invalid value for parameter 'weeks'");
    }

    @Test
    @DisplayName("handleAsyncRequestTimeout_shouldEndQuietly_forATimedOutStream (JOB-280)")
    void handleAsyncRequestTimeout_shouldEndQuietly_forATimedOutStream() {
        assertThatCode(() -> handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("handleGeneric_shouldLogErrorAndReturn500_forANormalRequest (JOB-329)")
    void handleGeneric_shouldLogErrorAndReturn500_forANormalRequest() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("unexpected db failure"), new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorLogs()).isEqualTo(1);
    }

    @Test
    @DisplayName("handleGeneric_shouldStayQuietAndWriteNoBody_whenTheStreamClientHasGone (JOB-329)")
    void handleGeneric_shouldStayQuietAndWriteNoBody_whenTheStreamClientHasGone() throws Exception {
        MockHttpServletResponse stream = openStream();
        AsyncRequestNotUsableException disconnect = new AsyncRequestNotUsableException(
                "ServletOutputStream failed to flush: java.io.IOException: Broken pipe",
                new IOException("Broken pipe"));

        assertThat(handler.handleGeneric(disconnect, stream)).isNull();
        assertThat(handler.handleGeneric(new IOException("Broken pipe"), stream)).isNull();
        assertThat(errorLogs()).isZero();
        assertThat(stream.getContentAsString()).isEqualTo(":heartbeat\n\n");
    }

    @Test
    @DisplayName("handleGeneric_shouldLogErrorButWriteNoBody_forAnotherErrorOnAStream (JOB-329)")
    void handleGeneric_shouldLogErrorButWriteNoBody_forAnotherErrorOnAStream() throws Exception {
        MockHttpServletResponse stream = openStream();

        assertThat(handler.handleGeneric(new RuntimeException("boom"), stream)).isNull();
        assertThat(errorLogs()).isEqualTo(1);
        assertThat(stream.getContentAsString()).isEqualTo(":heartbeat\n\n");
    }

    @Test
    @DisplayName("handleGeneric_shouldWriteNoBody_onAnEventStreamNotYetCommitted (JOB-329)")
    void handleGeneric_shouldWriteNoBody_onAnEventStreamNotYetCommitted() {
        MockHttpServletResponse stream = new MockHttpServletResponse();
        stream.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        assertThat(handler.handleGeneric(new RuntimeException("boom"), stream)).isNull();
    }

    @Test
    @DisplayName("handleGeneric_shouldStayQuietAndWriteNoBody_whenAnyClientHasGone (JOB-329)")
    void handleGeneric_shouldStayQuietAndWriteNoBody_whenAnyClientHasGone() {
        assertThat(handler.handleGeneric(new IOException("Connection reset by peer"), new MockHttpServletResponse()))
                .isNull();
        assertThat(errorLogs()).isZero();
    }

    @Test
    @DisplayName("handleGeneric_shouldWriteNoBody_whenTheResponseIsAlreadyCommitted (JOB-329)")
    void handleGeneric_shouldWriteNoBody_whenTheResponseIsAlreadyCommitted() throws Exception {
        MockHttpServletResponse committed = new MockHttpServletResponse();
        committed.setContentType(MediaType.APPLICATION_JSON_VALUE);
        committed.flushBuffer();

        assertThat(handler.handleGeneric(new RuntimeException("boom"), committed)).isNull();
        assertThat(errorLogs()).isEqualTo(1);
    }

    // An SSE response as the notification stream leaves it: text/event-stream, committed by the opening heartbeat.
    private static MockHttpServletResponse openStream() throws IOException {
        MockHttpServletResponse stream = new MockHttpServletResponse();
        stream.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        stream.getWriter().write(":heartbeat\n\n");
        stream.flushBuffer();
        return stream;
    }

    private long errorLogs() {
        return logs.list.stream().filter(event -> event.getLevel() == Level.ERROR).count();
    }
}
