package com.opsclear.service;

import com.opsclear.dto.NotificationResponse;
import com.opsclear.dto.ReadStateEvent;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPublisher")
class NotificationPublisherTest {

    @Mock private NotificationStreamRegistry streamRegistry;

    private NotificationPublisher publisher;
    private UUID userId;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(streamRegistry);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("publishCreated sends each notification to its own recipient as a notification event")
    void publishCreated_shouldSendEachToItsRecipient() {
        UUID otherUserId = UUID.randomUUID();
        NotificationModel mine = notification(userId);
        mine.setJobFriendlyId("JOB-280");
        NotificationModel theirs = notification(otherUserId);

        publisher.publishCreated(List.of(mine, theirs));

        ArgumentCaptor<Object> mineData = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> theirData = ArgumentCaptor.forClass(Object.class);
        verify(streamRegistry).sendToUser(eq(userId), eq(NotificationPublisher.NOTIFICATION_EVENT), mineData.capture());
        verify(streamRegistry)
                .sendToUser(eq(otherUserId), eq(NotificationPublisher.NOTIFICATION_EVENT), theirData.capture());
        verifyNoMoreInteractions(streamRegistry);
        NotificationResponse response = (NotificationResponse) mineData.getValue();
        assertThat(response.getId()).isEqualTo(mine.getId());
        assertThat(response.getJobFriendlyId()).isEqualTo("JOB-280");
        assertThat(((NotificationResponse) theirData.getValue()).getId()).isEqualTo(theirs.getId());
    }

    @Test
    @DisplayName("publishCreated with no notifications sends nothing")
    void publishCreated_shouldDoNothing_whenEmpty() {
        publisher.publishCreated(List.of());

        verifyNoInteractions(streamRegistry);
    }

    @Test
    @DisplayName("publishReadState sends a read-state event to the user")
    void publishReadState_shouldSendToUser() {
        ReadStateEvent state = ReadStateEvent.of(List.of(UUID.randomUUID()), true);

        publisher.publishReadState(userId, state);

        verify(streamRegistry).sendToUser(userId, NotificationPublisher.READ_STATE_EVENT, state);
    }

    private static NotificationModel notification(UUID recipientId) {
        return NotificationModel.builder()
                .id(UUID.randomUUID())
                .recipientId(recipientId)
                .type(NotificationType.NOTE_ADDED)
                .payload("{}")
                .build();
    }
}
