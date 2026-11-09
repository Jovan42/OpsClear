package com.opsclear.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.dto.ReadStateEvent;
import com.opsclear.event.NoteAddedEvent;
import com.opsclear.event.NotificationReadStateChangedEvent;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.NotificationStreamRegistry;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification SSE stream (ADR-0051 §SSE stream, JOB-280): {@code GET /api/notifications/stream} through the real
 * endpoints. The test profile runs the notification executor synchronously, so by the time a triggering request
 * returns, its events have been written to the open streams' (mock) responses: assertions read them directly, with no
 * waiting. Every "not delivered" check has a positive control in the same test (the event did reach someone, or the
 * same stream receives a later event), so a swallowed exception can't pass for a working guard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Notification SSE stream")
// Safety net: a stream left open must fail its test quickly instead of blocking the build until it times out.
@Timeout(60)
class NotificationStreamIntegrationTest {

    private static final String NOTIFICATION = "notification";
    private static final String READ_STATE = "read-state";
    private static final Duration COMPLETION_WAIT = Duration.ofSeconds(3);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private NotificationStreamRegistry streamRegistry;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID adminId;
    private UUID assigneeId;
    private UUID projectId;
    private UUID jobId;
    // The user sync filter overwrites email and name from the JWT, so requests carry the real ones.
    private final Map<UUID, UserModel> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        cleanUp();

        ownerId = user("owner@example.com", "Olga Owner");
        adminId = user("admin@example.com", "Adam Admin");
        assigneeId = user("assignee@example.com", "Ann Assignee");

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Stream Org").slug("SSE").createdBy(ownerId).build());
        organisationRepository.saveMember(org.getId(), ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(org.getId(), adminId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(org.getId(), assigneeId, OrganisationRole.MEMBER);

        projectId = projectRepository.save(ProjectModel.builder().name("Website Redesign")
                .friendlyId("PRJ-280").ownerId(ownerId).organisationId(org.getId()).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(adminId, ProjectMemberRole.ADMIN);
        addMember(assigneeId, ProjectMemberRole.MEMBER);

        jobId = jobRepository.save(JobModel.builder().friendlyId("JOB-280").projectId(projectId)
                .title("Fix login").status(JobStatus.IN_PROGRESS).assignedTo(assigneeId).createdBy(ownerId)
                .build()).getId();

        Set<UUID> addonIds = Set.of(addonId("NOTES"));
        subscriptionRepository.create(org.getId(), tierRepository.findAll().getFirst().getId(), "MONTHLY", addonIds);
        subscriptionRepository.updateFromPaddleWebhook(org.getId(), "sub_test_" + org.getId(), "ACTIVE", null, null);
    }

    // The registry is a singleton shared by the cached context, so close every stream a test opened.
    @AfterEach
    void cleanUp() {
        streamRegistry.closeAll();
        notificationRepository.deleteAll();
        noteRepository.deleteAll();
        jobRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Opening a stream ---

    @Test
    @DisplayName("GET /stream opens a text/event-stream with X-Accel-Buffering: no and a 30-minute timeout")
    void stream_shouldOpenEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get(ApiPaths.notificationStream()).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(result.getRequest().getAsyncContext().getTimeout()).isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(1);
        // Opens with a heartbeat comment, so the headers reach the client right away instead of at the first event.
        assertThat(result.getResponse().isCommitted()).isTrue();
        assertThat(body(result)).startsWith(":heartbeat\n\n");
    }

    @Test
    @DisplayName("GET /stream without authentication is 401 and opens nothing")
    void stream_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get(ApiPaths.notificationStream()))
                .andExpect(status().isUnauthorized())
                .andExpect(request().asyncNotStarted());
    }

    // --- notification events ---

    @Test
    @DisplayName("A notification created for the user is delivered as a notification event shaped like the list")
    void createdNotification_shouldBeDeliveredAsNotificationEvent() throws Exception {
        MvcResult stream = openStream(assigneeId);

        addNote(ownerId, "Found the cause");

        UUID notificationId = notificationIdFor(assigneeId);
        List<JsonNode> received = events(stream, NOTIFICATION);
        assertThat(received).hasSize(1);
        JsonNode event = received.getFirst();
        assertThat(event.get("id").asText()).isEqualTo(notificationId.toString());
        assertThat(event.get("type").asText()).isEqualTo(NotificationType.NOTE_ADDED.name());
        assertThat(event.get("read").asBoolean()).isFalse();
        assertThat(event.get("createdAt").isTextual()).isTrue();
        assertThat(event.get("actor").get("id").asText()).isEqualTo(ownerId.toString());
        assertThat(event.get("actor").get("name").asText()).isEqualTo("Olga Owner");
        assertThat(event.get("projectFriendlyId").asText()).isEqualTo("PRJ-280");
        assertThat(event.get("jobFriendlyId").asText()).isEqualTo("JOB-280");
        assertThat(event.get("payload").get("jobTitle").asText()).isEqualTo("Fix login");
    }

    @Test
    @DisplayName("Another user's notification is not delivered to an open stream")
    void otherUsersNotification_shouldNotBeDelivered() throws Exception {
        MvcResult assigneeStream = openStream(assigneeId);
        // The admin can see the job and has an open stream, so only the recipient check keeps the event away.
        MvcResult adminStream = openStream(adminId);
        assertThat(streamRegistry.streamCount(adminId)).isEqualTo(1);

        addNote(ownerId, "Found the cause");

        assertThat(recipients()).containsExactly(assigneeId);
        assertThat(events(assigneeStream, NOTIFICATION)).hasSize(1);
        assertThat(events(adminStream, NOTIFICATION)).isEmpty();
    }

    @Test
    @DisplayName("Every open stream (tab) of the recipient gets the notification")
    void createdNotification_shouldReachEveryStreamOfRecipient() throws Exception {
        MvcResult tab1 = openStream(assigneeId);
        MvcResult tab2 = openStream(assigneeId);

        addNote(ownerId, "Found the cause");

        assertThat(events(tab1, NOTIFICATION)).hasSize(1);
        assertThat(events(tab2, NOTIFICATION)).hasSize(1);
    }

    @Test
    @DisplayName("An event in a rolled-back transaction pushes nothing; the same event committed is pushed")
    void rolledBackTransaction_shouldPushNothing() throws Exception {
        MvcResult stream = openStream(assigneeId);
        NoteAddedEvent event = new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, ownerId);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });
        assertThat(recipients()).isEmpty();
        assertThat(events(stream, NOTIFICATION)).isEmpty();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        assertThat(events(stream, NOTIFICATION)).hasSize(1);
    }

    // --- read-state events ---

    @Test
    @DisplayName("Marking read and unread sends read-state events to all the caller's streams, not to other users")
    void markRead_shouldSendReadState() throws Exception {
        addNote(ownerId, "Found the cause");
        addNote(adminId, "Me too");
        UUID notificationId = notificationIdFor(assigneeId);
        MvcResult tab1 = openStream(assigneeId);
        MvcResult tab2 = openStream(assigneeId);
        // The owner was notified of the admin's note, so they have their own notification and an open stream.
        MvcResult ownerStream = openStream(ownerId);
        assertThat(recipients()).contains(ownerId);

        markRead(assigneeId, notificationId, true);
        markRead(assigneeId, notificationId, false);

        for (MvcResult tab : List.of(tab1, tab2)) {
            List<JsonNode> received = events(tab, READ_STATE);
            assertThat(received).hasSize(2);
            assertThat(received.get(0).toString())
                    .isEqualTo("{\"ids\":[\"" + notificationId + "\"],\"read\":true}");
            assertThat(received.get(1).toString())
                    .isEqualTo("{\"ids\":[\"" + notificationId + "\"],\"read\":false}");
        }
        assertThat(events(ownerStream, READ_STATE)).isEmpty();
    }

    @Test
    @DisplayName("Marking a notification to the state it already has sends no read-state event")
    void markRead_shouldSendNothing_whenUnchanged() throws Exception {
        addNote(ownerId, "Found the cause");
        UUID notificationId = notificationIdFor(assigneeId);
        MvcResult stream = openStream(assigneeId);

        markRead(assigneeId, notificationId, false);
        assertThat(events(stream, READ_STATE)).isEmpty();

        markRead(assigneeId, notificationId, true);
        assertThat(events(stream, READ_STATE)).hasSize(1);
    }

    @Test
    @DisplayName("Mark all read sends {all: true} only when something changed, and only to the caller")
    void markAllRead_shouldSendAllReadState() throws Exception {
        MvcResult stream = openStream(assigneeId);
        MvcResult adminStream = openStream(adminId);

        markAllRead(assigneeId);
        assertThat(events(stream, READ_STATE)).isEmpty();

        addNote(ownerId, "Found the cause");
        markAllRead(assigneeId);

        List<JsonNode> received = events(stream, READ_STATE);
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().toString()).isEqualTo("{\"all\":true}");
        assertThat(events(adminStream, READ_STATE)).isEmpty();
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.READ_AT.isNull())).isZero();
    }

    @Test
    @DisplayName("A read-state change in a rolled-back transaction pushes nothing; the same change committed is pushed")
    void rolledBackReadState_shouldPushNothing() throws Exception {
        MvcResult stream = openStream(assigneeId);
        NotificationReadStateChangedEvent event =
                new NotificationReadStateChangedEvent(assigneeId, ReadStateEvent.allRead());

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });
        assertThat(events(stream, READ_STATE)).isEmpty();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        assertThat(events(stream, READ_STATE)).hasSize(1);
    }

    // --- Limits, heartbeat and lifecycle ---

    @Test
    @DisplayName("A sixth stream closes the oldest one, which then gets no events; the other five do")
    void sixthStream_shouldCloseOldest() throws Exception {
        List<MvcResult> streams = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            streams.add(openStream(assigneeId));
        }
        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(5);

        // Eviction completed the oldest stream (closing the browser's connection): its dispatch ends normally.
        MvcResult oldest = streams.getFirst();
        dispatchCompleted(oldest).andExpect(status().isOk());

        addNote(ownerId, "Found the cause");

        assertThat(events(oldest, NOTIFICATION)).isEmpty();
        for (MvcResult stream : streams.subList(1, 6)) {
            assertThat(events(stream, NOTIFICATION)).hasSize(1);
        }
    }

    @Test
    @DisplayName("The heartbeat writes a comment line to every open stream of every user")
    void heartbeat_shouldReachEveryStream() throws Exception {
        MvcResult assigneeStream = openStream(assigneeId);
        MvcResult adminStream = openStream(adminId);
        // The scheduled heartbeat may also fire meanwhile, so compare counts rather than exact bodies.
        int assigneeBefore = heartbeats(assigneeStream);
        int adminBefore = heartbeats(adminStream);

        streamRegistry.sendHeartbeat();

        assertThat(heartbeats(assigneeStream)).isGreaterThan(assigneeBefore);
        assertThat(heartbeats(adminStream)).isGreaterThan(adminBefore);
    }

    @Test
    @DisplayName("A failed send drops that stream; the user's other stream still gets the event")
    void failedSend_shouldDropStream() throws Exception {
        MvcResult alive = openStream(assigneeId);
        // A stream whose connection already ended before any completion callback ran: sending to it fails.
        SseEmitter dead = streamRegistry.open(assigneeId);
        dead.complete();
        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(2);

        addNote(ownerId, "Found the cause");

        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(1);
        assertThat(events(alive, NOTIFICATION)).hasSize(1);
    }

    @Test
    @DisplayName("A stream is unregistered when it completes, times out or fails")
    void lifecycleCallbacks_shouldUnregister() throws Exception {
        MvcResult completed = openStream(assigneeId);
        MvcResult timedOut = openStream(assigneeId);
        MvcResult failed = openStream(assigneeId);
        MvcResult remaining = openStream(assigneeId);
        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(4);

        asyncContext(completed).complete();
        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(3);

        MockAsyncContext timeoutContext = asyncContext(timedOut);
        for (AsyncListener listener : List.copyOf(timeoutContext.getListeners())) {
            listener.onTimeout(new AsyncEvent(timeoutContext));
        }
        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(2);
        // Spring ends a timed-out stream with AsyncRequestTimeoutException: a normal end, not a 500 error body.
        MvcResult timeoutEnd = dispatchCompleted(timedOut).andExpect(status().isOk()).andReturn();
        assertThat(timeoutEnd.getResolvedException()).isInstanceOf(AsyncRequestTimeoutException.class);
        assertThat(body(timedOut)).doesNotContain("error");

        MockAsyncContext errorContext = asyncContext(failed);
        for (AsyncListener listener : List.copyOf(errorContext.getListeners())) {
            listener.onError(new AsyncEvent(errorContext, new IOException("Broken pipe")));
        }
        assertThat(streamRegistry.streamCount(assigneeId)).isEqualTo(1);

        addNote(ownerId, "Found the cause");
        assertThat(events(remaining, NOTIFICATION)).hasSize(1);
        assertThat(events(completed, NOTIFICATION)).isEmpty();
        assertThat(events(timedOut, NOTIFICATION)).isEmpty();
        assertThat(events(failed, NOTIFICATION)).isEmpty();

        // The user's last stream closing leaves them with none.
        asyncContext(remaining).complete();
        assertThat(streamRegistry.streamCount(assigneeId)).isZero();
    }

    @Test
    @DisplayName("Closing all streams (shutdown) completes every stream of every user")
    void closeAll_shouldCompleteEveryStream() throws Exception {
        MvcResult assigneeStream = openStream(assigneeId);
        MvcResult adminStream = openStream(adminId);

        streamRegistry.closeAll();

        assertThat(streamRegistry.streamCount(assigneeId)).isZero();
        assertThat(streamRegistry.streamCount(adminId)).isZero();
        dispatchCompleted(assigneeStream).andExpect(status().isOk());
        dispatchCompleted(adminStream).andExpect(status().isOk());
    }

    // --- Helpers ---

    private MvcResult openStream(UUID userId) throws Exception {
        return mockMvc.perform(get(ApiPaths.notificationStream()).with(as(userId)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    /**
     * Waits (bounded) for the stream to have completed, then runs its async dispatch. Never dispatch a stream that may
     * still be open: MockMvc's own wait is the stream's 30-minute timeout. Fails if it hasn't completed in time.
     */
    private ResultActions dispatchCompleted(MvcResult stream) throws Exception {
        stream.getAsyncResult(COMPLETION_WAIT.toMillis());
        return mockMvc.perform(asyncDispatch(stream));
    }

    private static MockAsyncContext asyncContext(MvcResult stream) {
        return (MockAsyncContext) stream.getRequest().getAsyncContext();
    }

    private static String body(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static int heartbeats(MvcResult stream) throws Exception {
        return body(stream).split(":heartbeat\n\n", -1).length - 1;
    }

    /** The data of every event with the given name written to the stream so far, in order. Comments are skipped. */
    private List<JsonNode> events(MvcResult stream, String name) throws Exception {
        List<JsonNode> found = new ArrayList<>();
        for (String block : body(stream).split("\n\n")) {
            String event = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length());
                } else if (line.startsWith("data:")) {
                    data.append(line.substring("data:".length()));
                }
            }
            if (name.equals(event)) {
                found.add(objectMapper.readTree(data.toString()));
            }
        }
        return found;
    }

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        users.put(id, userRepository.save(UserModel.builder().id(id).email(email).name(name).build()));
        return id;
    }

    private void addMember(UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(role).build());
    }

    private UUID addonId(String key) {
        return addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals(key)).findFirst().orElseThrow().getId();
    }

    private RequestPostProcessor as(UUID userId) {
        UserModel user = users.get(userId);
        return jwt().jwt(j -> j.subject(userId.toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName()));
    }

    private void addNote(UUID callerId, String content) throws Exception {
        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isCreated());
    }

    private void markRead(UUID callerId, UUID notificationId, boolean read) throws Exception {
        mockMvc.perform(patch(ApiPaths.notification(notificationId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("read", read))))
                .andExpect(status().isOk());
    }

    private void markAllRead(UUID callerId) throws Exception {
        mockMvc.perform(post(ApiPaths.NOTIFICATIONS_READ_ALL).with(as(callerId)))
                .andExpect(status().isNoContent());
    }

    private List<UUID> recipients() {
        return dsl.select(NOTIFICATIONS.RECIPIENT_ID).from(NOTIFICATIONS).fetch(NOTIFICATIONS.RECIPIENT_ID);
    }

    // The recipient's oldest notification.
    private UUID notificationIdFor(UUID recipientId) {
        return dsl.select(NOTIFICATIONS.ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
                .orderBy(NOTIFICATIONS.CREATED_AT, NOTIFICATIONS.ID)
                .limit(1)
                .fetchSingle(NOTIFICATIONS.ID);
    }
}
