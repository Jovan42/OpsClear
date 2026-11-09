package com.opsclear.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationStreamRegistry")
class NotificationStreamRegistryTest {

    private NotificationStreamRegistry registry;
    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        registry = new NotificationStreamRegistry();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    // --- open / register ---

    @Test
    @DisplayName("open registers a stream with the 30-minute timeout")
    void open_shouldRegisterStreamWithTimeout() {
        SseEmitter emitter = registry.open(userId);

        assertThat(emitter.getTimeout()).isEqualTo(30L * 60 * 1000);
        assertThat(registry.streamCount(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("A user without streams has none")
    void streamCount_shouldBeZero_whenNoneRegistered() {
        assertThat(registry.streamCount(userId)).isZero();
    }

    @Test
    @DisplayName("Completion removes the stream; the user's entry goes with their last stream")
    void onCompletion_shouldUnregister() {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        Runnable firstCompletion = register(userId, first).completion;
        Runnable secondCompletion = register(userId, second).completion;

        firstCompletion.run();
        assertThat(registry.streamCount(userId)).isEqualTo(1);

        secondCompletion.run();
        assertThat(registry.streamCount(userId)).isZero();
    }

    @Test
    @DisplayName("Timeout removes the stream (Spring completes it)")
    void onTimeout_shouldUnregister() {
        SseEmitter emitter = mock(SseEmitter.class);
        Runnable timeout = register(userId, emitter).timeout;

        timeout.run();

        assertThat(registry.streamCount(userId)).isZero();
    }

    @Test
    @DisplayName("An error removes the stream")
    void onError_shouldUnregister() {
        SseEmitter emitter = mock(SseEmitter.class);
        Consumer<Throwable> error = register(userId, emitter).error;

        error.accept(new IOException("broken pipe"));

        assertThat(registry.streamCount(userId)).isZero();
    }

    @Test
    @DisplayName("Unregistering a stream of a user without streams is a no-op")
    void unregister_shouldIgnoreUnknownUser() {
        registry.unregister(userId, mock(SseEmitter.class));

        assertThat(registry.streamCount(userId)).isZero();
    }

    @Test
    @DisplayName("A sixth stream closes the oldest, keeping five")
    void register_shouldEvictOldest_whenOverLimit() throws Exception {
        List<SseEmitter> streams = new ArrayList<>();
        for (int i = 0; i < NotificationStreamRegistry.MAX_STREAMS_PER_USER + 1; i++) {
            SseEmitter emitter = mock(SseEmitter.class);
            streams.add(emitter);
            registry.register(userId, emitter);
        }

        assertThat(registry.streamCount(userId)).isEqualTo(NotificationStreamRegistry.MAX_STREAMS_PER_USER);
        verify(streams.getFirst()).complete();
        verify(streams.get(1), never()).complete();

        registry.sendToUser(userId, "notification", Map.of("id", "n1"));
        verify(streams.getFirst(), never()).send(any(SseEventBuilder.class));
        verify(streams.getLast()).send(any(SseEventBuilder.class));
    }

    @Test
    @DisplayName("Five streams are allowed without evicting any")
    void register_shouldKeepAll_atLimit() {
        SseEmitter first = mock(SseEmitter.class);
        registry.register(userId, first);
        for (int i = 1; i < NotificationStreamRegistry.MAX_STREAMS_PER_USER; i++) {
            registry.register(userId, mock(SseEmitter.class));
        }

        assertThat(registry.streamCount(userId)).isEqualTo(NotificationStreamRegistry.MAX_STREAMS_PER_USER);
        verify(first, never()).complete();
    }

    // --- sendToUser ---

    @Test
    @DisplayName("sendToUser writes a named JSON event to every stream of the target user only")
    void sendToUser_shouldSendToTargetUserOnly() throws Exception {
        SseEmitter tab1 = mock(SseEmitter.class);
        SseEmitter tab2 = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        registry.register(userId, tab1);
        registry.register(userId, tab2);
        registry.register(otherUserId, other);

        registry.sendToUser(userId, "read-state", Map.of("all", true));

        ArgumentCaptor<SseEventBuilder> event = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(tab1).send(event.capture());
        verify(tab2).send(any(SseEventBuilder.class));
        verify(other, never()).send(any(SseEventBuilder.class));
        assertThat(render(event.getValue())).startsWith("event:read-state\ndata:");
    }

    @Test
    @DisplayName("sendToUser does nothing for a user without streams")
    void sendToUser_shouldDoNothing_whenNoStreams() throws Exception {
        SseEmitter other = mock(SseEmitter.class);
        registry.register(otherUserId, other);

        registry.sendToUser(userId, "notification", Map.of());

        verify(other, never()).send(any(SseEventBuilder.class));
    }

    @Test
    @DisplayName("A failed send drops that stream and completes it with the error; other streams still receive")
    void sendToUser_shouldDropStream_whenSendFails() throws Exception {
        SseEmitter dead = mock(SseEmitter.class);
        SseEmitter alive = mock(SseEmitter.class);
        IOException failure = new IOException("broken pipe");
        doThrow(failure).when(dead).send(any(SseEventBuilder.class));
        registry.register(userId, dead);
        registry.register(userId, alive);

        registry.sendToUser(userId, "notification", Map.of());

        assertThat(registry.streamCount(userId)).isEqualTo(1);
        verify(dead).completeWithError(failure);
        verify(alive).send(any(SseEventBuilder.class));
    }

    @Test
    @DisplayName("Sending to an already completed stream drops it")
    void sendToUser_shouldDropStream_whenAlreadyCompleted() throws Exception {
        SseEmitter completed = mock(SseEmitter.class);
        doThrow(new IllegalStateException("already completed")).when(completed).send(any(SseEventBuilder.class));
        registry.register(userId, completed);

        registry.sendToUser(userId, "notification", Map.of());

        assertThat(registry.streamCount(userId)).isZero();
    }

    // --- heartbeat ---

    @Test
    @DisplayName("The heartbeat writes a comment line to every stream of every user")
    void sendHeartbeat_shouldReachAllStreams() throws Exception {
        SseEmitter tab1 = mock(SseEmitter.class);
        SseEmitter tab2 = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        registry.register(userId, tab1);
        registry.register(userId, tab2);
        registry.register(otherUserId, other);

        registry.sendHeartbeat();

        ArgumentCaptor<SseEventBuilder> event = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(tab1).send(event.capture());
        verify(tab2).send(any(SseEventBuilder.class));
        verify(other).send(any(SseEventBuilder.class));
        assertThat(render(event.getValue())).isEqualTo(":heartbeat\n\n");
    }

    @Test
    @DisplayName("A failed heartbeat drops the dead stream")
    void sendHeartbeat_shouldDropStream_whenSendFails() throws Exception {
        SseEmitter dead = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(dead).send(any(SseEventBuilder.class));
        registry.register(userId, dead);
        registry.register(otherUserId, mock(SseEmitter.class));

        registry.sendHeartbeat();

        assertThat(registry.streamCount(userId)).isZero();
        assertThat(registry.streamCount(otherUserId)).isEqualTo(1);
    }

    // --- closeAll ---

    @Test
    @DisplayName("closeAll completes and forgets every stream")
    void closeAll_shouldCompleteAllStreams() {
        SseEmitter mine = mock(SseEmitter.class);
        SseEmitter other = mock(SseEmitter.class);
        registry.register(userId, mine);
        registry.register(otherUserId, other);

        registry.closeAll();

        verify(mine).complete();
        verify(other).complete();
        assertThat(registry.streamCount(userId)).isZero();
        assertThat(registry.streamCount(otherUserId)).isZero();
    }

    // --- Helpers ---

    private Callbacks register(UUID user, SseEmitter emitter) {
        registry.register(user, emitter);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> error = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onCompletion(completion.capture());
        verify(emitter).onTimeout(timeout.capture());
        verify(emitter).onError(error.capture());
        return new Callbacks(completion.getValue(), timeout.getValue(), error.getValue());
    }

    private static String render(SseEventBuilder event) {
        StringBuilder text = new StringBuilder();
        event.build().forEach(part -> text.append(part.getData()));
        return text.toString();
    }

    private record Callbacks(Runnable completion, Runnable timeout, Consumer<Throwable> error) {
    }
}
