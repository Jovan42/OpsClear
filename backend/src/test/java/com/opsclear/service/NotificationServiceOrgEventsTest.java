package com.opsclear.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.FeedbackResolvedEvent;
import com.opsclear.event.InviteAcceptedEvent;
import com.opsclear.event.ProjectMemberAddedEvent;
import com.opsclear.event.SubscriptionPastDueEvent;
import com.opsclear.model.FeedbackStatus;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService fan-out: project, organisation and billing events")
class NotificationServiceOrgEventsTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Captor private ArgumentCaptor<List<NotificationModel>> batchCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationService notificationService;

    private UUID orgId;
    private UUID projectId;
    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID newUserId;
    private ProjectModel project;
    private OrganisationModel org;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationSettingsService,
                jobRepository, projectRepository, projectMemberRepository, noteRepository, userRepository,
                objectMapper, organisationRepository);
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        newUserId = UUID.randomUUID();
        project = ProjectModel.builder()
                .id(projectId).friendlyId("PRJ-012").name("Website Redesign").organisationId(orgId).build();
        org = OrganisationModel.builder().id(orgId).name("Acme").build();
    }

    // --- Helpers ---

    private void givenActor(UUID actorId, String name) {
        when(userRepository.findById(actorId))
                .thenReturn(Optional.of(UserModel.builder().id(actorId).name(name).build()));
    }

    private void givenNoMutes(UUID... userIds) {
        for (UUID userId : userIds) {
            when(notificationSettingsService.getMutedTypes(userId)).thenReturn(Set.of());
        }
    }

    private void givenInsertReturnsInput() {
        when(notificationRepository.insertBatch(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private List<NotificationModel> captureBatch() {
        verify(notificationRepository).insertBatch(batchCaptor.capture());
        return batchCaptor.getValue();
    }

    private JsonNode payload(NotificationModel notification) throws Exception {
        return objectMapper.readTree(notification.getPayload());
    }

    private static OrgMemberModel orgMember(UUID userId, OrganisationRole role, String name, String email) {
        return OrgMemberModel.builder().userId(userId).role(role).userName(name).userEmail(email).build();
    }

    // Owner, Admin, Member and the newly joined user (a Member).
    private void givenOrgWithMembers() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMembers(orgId)).thenReturn(List.of(
                orgMember(ownerId, OrganisationRole.OWNER, "Olga Owner", "olga@example.com"),
                orgMember(adminId, OrganisationRole.ADMIN, "Adam Admin", "adam@example.com"),
                orgMember(memberId, OrganisationRole.MEMBER, "Mia Member", "mia@example.com"),
                orgMember(newUserId, OrganisationRole.MEMBER, "Nina New", "nina@example.com")));
    }

    // --- PROJECT_MEMBER_ADDED ---

    @Test
    @DisplayName("PROJECT_MEMBER_ADDED notifies the added user with project and role, no job")
    void notifyProjectMemberAdded_shouldNotifyAddedUser() throws Exception {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, newUserId)).thenReturn(true);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(newUserId);
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifyProjectMemberAdded(
                new ProjectMemberAddedEvent(projectId, newUserId, ProjectMemberRole.ADMIN, ownerId));

        assertThat(saved).hasSize(1);
        NotificationModel n = captureBatch().getFirst();
        assertThat(n.getType()).isEqualTo(NotificationType.PROJECT_MEMBER_ADDED);
        assertThat(n.getRecipientId()).isEqualTo(newUserId);
        assertThat(n.getOrgId()).isEqualTo(orgId);
        assertThat(n.getProjectId()).isEqualTo(projectId);
        assertThat(n.getJobId()).isNull();
        assertThat(n.getApprovalId()).isNull();
        assertThat(n.getActorId()).isEqualTo(ownerId);
        JsonNode payload = payload(n);
        assertThat(payload.get("projectFriendlyId").asText()).isEqualTo("PRJ-012");
        assertThat(payload.get("projectName").asText()).isEqualTo("Website Redesign");
        assertThat(payload.get("actorName").asText()).isEqualTo("Olga Owner");
        assertThat(payload.get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("PROJECT_MEMBER_ADDED doesn't notify the actor when they added themselves")
    void notifyProjectMemberAdded_shouldExcludeActor() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, ownerId)).thenReturn(true);
        givenActor(ownerId, "Olga Owner");

        List<NotificationModel> saved = notificationService.notifyProjectMemberAdded(
                new ProjectMemberAddedEvent(projectId, ownerId, ProjectMemberRole.MEMBER, ownerId));

        assertThat(saved).isEmpty();
        verifyNoInteractions(notificationRepository);
        verify(notificationSettingsService, never()).getMutedTypes(any());
    }

    @Test
    @DisplayName("PROJECT_MEMBER_ADDED notifies no one when the user was removed again before the fan-out")
    void notifyProjectMemberAdded_shouldSkip_whenNoLongerMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, newUserId)).thenReturn(false);
        givenActor(ownerId, "Olga Owner");

        assertThat(notificationService.notifyProjectMemberAdded(
                new ProjectMemberAddedEvent(projectId, newUserId, ProjectMemberRole.MEMBER, ownerId))).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("PROJECT_MEMBER_ADDED notifies no one when the project was deleted before the fan-out")
    void notifyProjectMemberAdded_shouldSkip_whenProjectDeleted() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifyProjectMemberAdded(
                new ProjectMemberAddedEvent(projectId, newUserId, ProjectMemberRole.MEMBER, ownerId))).isEmpty();
        verifyNoInteractions(notificationRepository, notificationSettingsService);
    }

    @Test
    @DisplayName("PROJECT_MEMBER_ADDED respects the added user's mute")
    void notifyProjectMemberAdded_shouldRespectMute() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, newUserId)).thenReturn(true);
        givenActor(ownerId, "Olga Owner");
        when(notificationSettingsService.getMutedTypes(newUserId))
                .thenReturn(Set.of(NotificationType.PROJECT_MEMBER_ADDED));

        assertThat(notificationService.notifyProjectMemberAdded(
                new ProjectMemberAddedEvent(projectId, newUserId, ProjectMemberRole.MEMBER, ownerId))).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    // --- INVITE_ACCEPTED ---

    @Test
    @DisplayName("INVITE_ACCEPTED notifies the inviter (org-level, no project) with the new member's name and email")
    void notifyInviteAccepted_shouldNotifyInviter() throws Exception {
        givenOrgWithMembers();
        givenNoMutes(memberId);
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifyInviteAccepted(
                new InviteAcceptedEvent(UUID.randomUUID(), orgId, newUserId, memberId));

        assertThat(saved).hasSize(1);
        NotificationModel n = captureBatch().getFirst();
        assertThat(n.getType()).isEqualTo(NotificationType.INVITE_ACCEPTED);
        assertThat(n.getRecipientId()).isEqualTo(memberId);
        assertThat(n.getOrgId()).isEqualTo(orgId);
        assertThat(n.getProjectId()).isNull();
        assertThat(n.getJobId()).isNull();
        assertThat(n.getActorId()).isEqualTo(newUserId);
        JsonNode payload = payload(n);
        assertThat(payload.get("orgName").asText()).isEqualTo("Acme");
        assertThat(payload.get("actorName").asText()).isEqualTo("Nina New");
        assertThat(payload.get("memberName").asText()).isEqualTo("Nina New");
        assertThat(payload.get("memberEmail").asText()).isEqualTo("nina@example.com");
    }

    @Test
    @DisplayName("INVITE_ACCEPTED falls back to the org's Owners and Admins when the inviter left the org")
    void notifyInviteAccepted_shouldFallBackToOwnersAndAdmins_whenInviterLeft() {
        givenOrgWithMembers();
        givenNoMutes(ownerId, adminId);
        givenInsertReturnsInput();

        notificationService.notifyInviteAccepted(
                new InviteAcceptedEvent(UUID.randomUUID(), orgId, newUserId, UUID.randomUUID()));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(ownerId, adminId);
    }

    @Test
    @DisplayName("INVITE_ACCEPTED falls back to the org's Owners and Admins when the invite has no inviter")
    void notifyInviteAccepted_shouldFallBackToOwnersAndAdmins_whenNoInviter() {
        givenOrgWithMembers();
        givenNoMutes(ownerId, adminId);
        givenInsertReturnsInput();

        notificationService.notifyInviteAccepted(new InviteAcceptedEvent(UUID.randomUUID(), orgId, newUserId, null));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(ownerId, adminId);
    }

    @Test
    @DisplayName("INVITE_ACCEPTED leaves the member fields out when the new member already left again")
    void notifyInviteAccepted_shouldOmitMemberFields_whenNewMemberGone() throws Exception {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(organisationRepository.findMembers(orgId)).thenReturn(List.of(
                orgMember(ownerId, OrganisationRole.OWNER, "Olga Owner", "olga@example.com")));
        givenNoMutes(ownerId);
        givenInsertReturnsInput();

        notificationService.notifyInviteAccepted(
                new InviteAcceptedEvent(UUID.randomUUID(), orgId, newUserId, ownerId));

        JsonNode payload = payload(captureBatch().getFirst());
        assertThat(payload.has("memberName")).isFalse();
        assertThat(payload.has("memberEmail")).isFalse();
        assertThat(payload.has("actorName")).isFalse();
    }

    @Test
    @DisplayName("INVITE_ACCEPTED notifies no one when the organisation was deleted before the fan-out")
    void notifyInviteAccepted_shouldSkip_whenOrgDeleted() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifyInviteAccepted(
                new InviteAcceptedEvent(UUID.randomUUID(), orgId, newUserId, ownerId))).isEmpty();
        verifyNoInteractions(notificationRepository, notificationSettingsService);
        verify(organisationRepository, never()).findMembers(any());
    }

    // --- FEEDBACK_RESOLVED ---

    @Test
    @DisplayName("FEEDBACK_RESOLVED (credited) notifies the submitter with outcome and amount, no actor")
    void notifyFeedbackResolved_shouldNotifySubmitter_whenCredited() throws Exception {
        givenOrgWithMembers();
        givenNoMutes(memberId);
        givenInsertReturnsInput();

        notificationService.notifyFeedbackResolved(new FeedbackResolvedEvent(
                UUID.randomUUID(), orgId, memberId, "Broken button", FeedbackStatus.CREDITED, 1000));

        NotificationModel n = captureBatch().getFirst();
        assertThat(n.getType()).isEqualTo(NotificationType.FEEDBACK_RESOLVED);
        assertThat(n.getRecipientId()).isEqualTo(memberId);
        assertThat(n.getActorId()).isNull();
        assertThat(n.getProjectId()).isNull();
        assertThat(n.getJobId()).isNull();
        JsonNode payload = payload(n);
        assertThat(payload.get("orgName").asText()).isEqualTo("Acme");
        assertThat(payload.has("actorName")).isFalse();
        assertThat(payload.get("feedbackTitle").asText()).isEqualTo("Broken button");
        assertThat(payload.get("outcome").asText()).isEqualTo("CREDITED");
        assertThat(payload.get("creditAmount").asInt()).isEqualTo(1000);
    }

    @Test
    @DisplayName("FEEDBACK_RESOLVED (declined) notifies the submitter without a credit amount")
    void notifyFeedbackResolved_shouldNotifySubmitter_whenDeclined() throws Exception {
        givenOrgWithMembers();
        givenNoMutes(memberId);
        givenInsertReturnsInput();

        notificationService.notifyFeedbackResolved(new FeedbackResolvedEvent(
                UUID.randomUUID(), orgId, memberId, "Idea", FeedbackStatus.DECLINED, null));

        JsonNode payload = payload(captureBatch().getFirst());
        assertThat(payload.get("outcome").asText()).isEqualTo("DECLINED");
        assertThat(payload.has("creditAmount")).isFalse();
    }

    @Test
    @DisplayName("FEEDBACK_RESOLVED notifies no one when the submitter has left the organisation")
    void notifyFeedbackResolved_shouldSkip_whenSubmitterLeftOrg() {
        givenOrgWithMembers();

        assertThat(notificationService.notifyFeedbackResolved(new FeedbackResolvedEvent(
                UUID.randomUUID(), orgId, UUID.randomUUID(), "Idea", FeedbackStatus.DECLINED, null))).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("FEEDBACK_RESOLVED respects the submitter's mute")
    void notifyFeedbackResolved_shouldRespectMute() {
        givenOrgWithMembers();
        when(notificationSettingsService.getMutedTypes(memberId))
                .thenReturn(Set.of(NotificationType.FEEDBACK_RESOLVED));

        assertThat(notificationService.notifyFeedbackResolved(new FeedbackResolvedEvent(
                UUID.randomUUID(), orgId, memberId, "Idea", FeedbackStatus.DECLINED, null))).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    // --- SUBSCRIPTION_PAST_DUE ---

    @Test
    @DisplayName("SUBSCRIPTION_PAST_DUE notifies the org's Owners and Admins (org-level, no actor)")
    void notifySubscriptionPastDue_shouldNotifyOwnersAndAdmins() throws Exception {
        givenOrgWithMembers();
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifySubscriptionPastDue(
                new SubscriptionPastDueEvent(orgId));

        assertThat(saved).hasSize(2);
        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId).containsExactly(ownerId, adminId);
        NotificationModel n = batch.getFirst();
        assertThat(n.getType()).isEqualTo(NotificationType.SUBSCRIPTION_PAST_DUE);
        assertThat(n.getOrgId()).isEqualTo(orgId);
        assertThat(n.getActorId()).isNull();
        assertThat(n.getProjectId()).isNull();
        assertThat(n.getJobId()).isNull();
        assertThat(payload(n).get("orgName").asText()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("SUBSCRIPTION_PAST_DUE ignores mutes: it isn't mutable, so mutes aren't even read")
    void notifySubscriptionPastDue_shouldIgnoreMutes() {
        givenOrgWithMembers();
        givenInsertReturnsInput();

        notificationService.notifySubscriptionPastDue(new SubscriptionPastDueEvent(orgId));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(ownerId, adminId);
        verifyNoInteractions(notificationSettingsService);
    }

    @Test
    @DisplayName("SUBSCRIPTION_PAST_DUE notifies no one when the organisation was deleted")
    void notifySubscriptionPastDue_shouldSkip_whenOrgDeleted() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifySubscriptionPastDue(new SubscriptionPastDueEvent(orgId))).isEmpty();
        verifyNoInteractions(notificationRepository);
    }
}
