package com.opsclear.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Open notification streams (ADR-0051 §SSE stream), in memory and per backend instance.
 *
 * <p>Each user may hold at most {@value #MAX_STREAMS_PER_USER} streams (one per tab); opening another closes the
 * oldest. Streams are removed on completion, timeout and error, and when a send to them fails. Sends are written
 * straight to the emitters: there is no per-connection queue and no replay, because clients re-fetch on (re)connect.
 * A single scheduled heartbeat writes a comment line to every stream so proxies keep idle connections open and dead
 * clients are detected.
 *
 * <p>Thread safety: requests, the notification executor and the heartbeat use the registry concurrently. Each user's
 * list is only changed inside {@code compute}/{@code computeIfPresent} on the map, which are atomic per user, and is a
 * {@link CopyOnWriteArrayList}, so sends iterate a stable snapshot. Emitters are completed outside those calls, because
 * completing one runs its callbacks, which update the map again. {@link SseEmitter#send} is itself thread-safe.
 */
@Service
@Slf4j
public class NotificationStreamRegistry {

    static final int MAX_STREAMS_PER_USER = 5;
    // Closing after 30 minutes makes the client reconnect with a fresh token, so a stream never outlives the
    // access token it was opened with.
    static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    static final long HEARTBEAT_INTERVAL_MS = 25_000;
    static final String HEARTBEAT_COMMENT = "heartbeat";

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Opens a stream for the user with the 30-minute timeout and registers it. The stream starts with a heartbeat
     * comment: Spring writes the response headers only with the first data, so without it the client (and nginx)
     * would wait up to one heartbeat interval for the stream to open.
     */
    public SseEmitter open(UUID userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        register(userId, emitter);
        send(userId, emitter, heartbeat());
        return emitter;
    }

    /**
     * Registers an emitter for the user and wires its completion, timeout and error callbacks to remove it. If the
     * user now has more than {@value #MAX_STREAMS_PER_USER} streams, the oldest is closed. On timeout Spring marks the
     * emitter complete itself and ends the request with an {@code AsyncRequestTimeoutException}, which
     * {@code GlobalExceptionHandler} treats as a normal end of the stream.
     */
    void register(UUID userId, SseEmitter emitter) {
        emitter.onCompletion(() -> unregister(userId, emitter));
        emitter.onTimeout(() -> unregister(userId, emitter));
        emitter.onError(error -> unregister(userId, emitter));

        List<SseEmitter> evicted = new ArrayList<>();
        emitters.compute(userId, (id, streams) -> {
            List<SseEmitter> list = streams != null ? streams : new CopyOnWriteArrayList<>();
            list.add(emitter);
            while (list.size() > MAX_STREAMS_PER_USER) {
                evicted.add(list.removeFirst());
            }
            return list;
        });
        evicted.forEach(SseEmitter::complete);
        if (!evicted.isEmpty()) {
            log.info("Closed {} oldest notification stream(s) of user {}: over the limit of {}",
                    evicted.size(), userId, MAX_STREAMS_PER_USER);
        }
    }

    /** Removes the emitter; the user's entry goes away with their last stream. */
    void unregister(UUID userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, streams) -> {
            streams.remove(emitter);
            return streams.isEmpty() ? null : streams;
        });
    }

    /** The number of streams the user has open. */
    public int streamCount(UUID userId) {
        List<SseEmitter> streams = emitters.get(userId);
        return streams != null ? streams.size() : 0;
    }

    /** Sends a named event to every open stream of the user. A failed send drops that stream. */
    public void sendToUser(UUID userId, String eventName, Object data) {
        List<SseEmitter> streams = emitters.get(userId);
        if (streams == null) {
            return;
        }
        for (SseEmitter emitter : streams) {
            send(userId, emitter, SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        }
    }

    /** Writes a heartbeat comment to every open stream. A failed send drops that stream. */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeat() {
        emitters.forEach((userId, streams) -> {
            for (SseEmitter emitter : streams) {
                send(userId, emitter, heartbeat());
            }
        });
    }

    /** Closes every open stream, so shutdown isn't held up by long-lived connections. */
    @PreDestroy
    public void closeAll() {
        List<SseEmitter> all = new ArrayList<>();
        emitters.values().forEach(all::addAll);
        emitters.clear();
        all.forEach(SseEmitter::complete);
    }

    private static SseEmitter.SseEventBuilder heartbeat() {
        return SseEmitter.event().comment(HEARTBEAT_COMMENT);
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            // IOException: the client went away. IllegalStateException: the emitter has already completed.
            log.debug("Dropping notification stream of user {}: {}", userId, e.getMessage());
            unregister(userId, emitter);
            emitter.completeWithError(e);
        }
    }
}
