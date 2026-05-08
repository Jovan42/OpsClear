package com.opsclear.service;

import com.opsclear.dto.CreateJobTemplateRequest;
import com.opsclear.dto.UpdateJobTemplateRequest;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobTemplateService")
class JobTemplateServiceTest {

    @Mock private JobTemplateRepository jobTemplateRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private FriendlyIdService friendlyIdService;

    private JobTemplateService jobTemplateService;

    private UUID projectId;
    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID orgId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel adminMembership;
    private ProjectMemberModel memberMembership;

    @BeforeEach
    void setUp() {
        jobTemplateService = new JobTemplateService(
                jobTemplateRepository, projectRepository, projectMemberRepository,
                organisationRepository, friendlyIdService);

        projectId = UUID.randomUUID();
        ownerId   = UUID.randomUUID();
        adminId   = UUID.randomUUID();
        memberId  = UUID.randomUUID();
        orgId     = UUID.randomUUID();

        project = ProjectModel.builder().id(projectId).name("Test Project").ownerId(ownerId).build();

        ownerMembership  = ProjectMemberModel.builder().projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();
        adminMembership  = ProjectMemberModel.builder().projectId(projectId).userId(adminId).role(ProjectMemberRole.ADMIN).build();
        memberMembership = ProjectMemberModel.builder().projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();
    }

    // --- list ---

    @Test
    @DisplayName("list — member can list templates")
    void list_shouldReturnTemplates_forMember() {
        List<JobTemplateModel> templates = List.of(
                JobTemplateModel.builder().id(UUID.randomUUID()).projectId(projectId).name("Daily Standup").build()
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(jobTemplateRepository.findActiveByProjectId(projectId)).thenReturn(templates);

        List<JobTemplateModel> result = jobTemplateService.list(projectId, memberId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Daily Standup");
    }

    @Test
    @DisplayName("list — throws NotFoundException when project does not exist")
    void list_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTemplateService.list(projectId, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("list — throws ForbiddenException when requester is not a member")
    void list_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTemplateService.list(projectId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- create ---

    @Test
    @DisplayName("create — owner creates template with stripped name and friendly ID")
    void create_shouldCreateTemplate_forOwner() {
        CreateJobTemplateRequest request = new CreateJobTemplateRequest();
        request.setName("  Weekly Report  ");
        request.setTitle("Report for {{week}}");
        request.setAssigneeMode("NONE");

        OrganisationModel org = OrganisationModel.builder().id(orgId).name("Acme").slug("ACM").createdBy(ownerId).build();
        JobTemplateModel saved = JobTemplateModel.builder()
                .id(UUID.randomUUID()).friendlyId("TPL-001").projectId(projectId)
                .name("Weekly Report").assigneeMode("NONE").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.of(org));
        when(friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.TEMPLATE)).thenReturn("TPL-001");
        when(jobTemplateRepository.save(any())).thenReturn(saved);

        JobTemplateModel result = jobTemplateService.create(projectId, request, ownerId);

        assertThat(result.getName()).isEqualTo("Weekly Report");
        assertThat(result.getFriendlyId()).isEqualTo("TPL-001");

        ArgumentCaptor<JobTemplateModel> captor = ArgumentCaptor.forClass(JobTemplateModel.class);
        verify(jobTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Weekly Report");
        assertThat(captor.getValue().getFriendlyId()).isEqualTo("TPL-001");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("create — admin can create a template")
    void create_shouldCreateTemplate_forAdmin() {
        CreateJobTemplateRequest request = new CreateJobTemplateRequest();
        request.setName("Inspection");
        request.setAssigneeMode("FIXED");

        OrganisationModel org = OrganisationModel.builder().id(orgId).name("Acme").slug("ACM").createdBy(adminId).build();
        JobTemplateModel saved = JobTemplateModel.builder()
                .id(UUID.randomUUID()).friendlyId("TPL-002").projectId(projectId)
                .name("Inspection").assigneeMode("FIXED").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId)).thenReturn(Optional.of(adminMembership));
        when(organisationRepository.findByMember(adminId)).thenReturn(Optional.of(org));
        when(friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.TEMPLATE)).thenReturn("TPL-002");
        when(jobTemplateRepository.save(any())).thenReturn(saved);

        JobTemplateModel result = jobTemplateService.create(projectId, request, adminId);

        assertThat(result.getName()).isEqualTo("Inspection");
    }

    @Test
    @DisplayName("create — friendlyId is null when user has no org")
    void create_shouldSetNullFriendlyId_whenUserHasNoOrg() {
        CreateJobTemplateRequest request = new CreateJobTemplateRequest();
        request.setName("Orphan");
        request.setAssigneeMode("NONE");

        JobTemplateModel saved = JobTemplateModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).name("Orphan").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());
        when(jobTemplateRepository.save(any())).thenReturn(saved);

        jobTemplateService.create(projectId, request, ownerId);

        ArgumentCaptor<JobTemplateModel> captor = ArgumentCaptor.forClass(JobTemplateModel.class);
        verify(jobTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getFriendlyId()).isNull();
    }

    @Test
    @DisplayName("create — assigneeMode defaults to NONE when explicitly null")
    void create_shouldDefaultAssigneeModeToNone_whenNull() {
        CreateJobTemplateRequest request = new CreateJobTemplateRequest();
        request.setName("Template");
        request.setAssigneeMode(null);

        OrganisationModel org = OrganisationModel.builder().id(orgId).name("Acme").slug("ACM").createdBy(ownerId).build();
        JobTemplateModel saved = JobTemplateModel.builder().id(UUID.randomUUID()).projectId(projectId).name("Template").assigneeMode("NONE").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.of(org));
        when(friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.TEMPLATE)).thenReturn("TPL-001");
        when(jobTemplateRepository.save(any())).thenReturn(saved);

        jobTemplateService.create(projectId, request, ownerId);

        ArgumentCaptor<JobTemplateModel> captor = ArgumentCaptor.forClass(JobTemplateModel.class);
        verify(jobTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getAssigneeMode()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("create — member is forbidden")
    void create_shouldThrow_whenMemberRole() {
        CreateJobTemplateRequest request = new CreateJobTemplateRequest();
        request.setName("T1");

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobTemplateService.create(projectId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("create — throws ForbiddenException when requester is not a project member")
    void create_shouldThrow_whenNotMember() {
        CreateJobTemplateRequest request = new CreateJobTemplateRequest();
        request.setName("T1");

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTemplateService.create(projectId, request, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- update ---

    @Test
    @DisplayName("update — owner can update a template")
    void update_shouldUpdateTemplate_forOwner() {
        UUID templateId = UUID.randomUUID();
        JobTemplateModel existing = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("Old").assigneeMode("NONE").build();
        UpdateJobTemplateRequest request = new UpdateJobTemplateRequest();
        request.setName("  New Name  ");
        request.setAssigneeMode("FIXED");
        JobTemplateModel updated = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("New Name").assigneeMode("FIXED").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(jobTemplateRepository.save(any())).thenReturn(updated);

        JobTemplateModel result = jobTemplateService.update(projectId, templateId, request, ownerId);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getAssigneeMode()).isEqualTo("FIXED");
    }

    @Test
    @DisplayName("update — preserves existing assigneeMode when request has null assigneeMode")
    void update_shouldPreserveAssigneeMode_whenNullInRequest() {
        UUID templateId = UUID.randomUUID();
        JobTemplateModel existing = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("Old").assigneeMode("ASK").build();
        UpdateJobTemplateRequest request = new UpdateJobTemplateRequest();
        request.setName("New");
        // assigneeMode not set (null)
        JobTemplateModel updated = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("New").assigneeMode("ASK").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(jobTemplateRepository.save(any())).thenReturn(updated);

        jobTemplateService.update(projectId, templateId, request, ownerId);

        assertThat(existing.getAssigneeMode()).isEqualTo("ASK");
    }

    @Test
    @DisplayName("update — throws NotFoundException when template does not exist")
    void update_shouldThrow_whenTemplateNotFound() {
        UUID templateId = UUID.randomUUID();
        UpdateJobTemplateRequest request = new UpdateJobTemplateRequest();
        request.setName("X");

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTemplateService.update(projectId, templateId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job template not found");
    }

    @Test
    @DisplayName("update — throws NotFoundException when template belongs to a different project")
    void update_shouldThrow_whenTemplateBelongsToDifferentProject() {
        UUID templateId = UUID.randomUUID();
        JobTemplateModel otherTemplate = JobTemplateModel.builder()
                .id(templateId).projectId(UUID.randomUUID()).name("Other").build();
        UpdateJobTemplateRequest request = new UpdateJobTemplateRequest();
        request.setName("X");

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(otherTemplate));

        assertThatThrownBy(() -> jobTemplateService.update(projectId, templateId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job template not found");
    }

    @Test
    @DisplayName("update — member is forbidden")
    void update_shouldThrow_whenMemberRole() {
        UUID templateId = UUID.randomUUID();
        UpdateJobTemplateRequest request = new UpdateJobTemplateRequest();
        request.setName("X");

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobTemplateService.update(projectId, templateId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    // --- softDelete ---

    @Test
    @DisplayName("softDelete — owner can soft delete a template")
    void softDelete_shouldSoftDeleteTemplate_forOwner() {
        UUID templateId = UUID.randomUUID();
        JobTemplateModel template = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("T1").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        jobTemplateService.softDelete(projectId, templateId, ownerId);

        verify(jobTemplateRepository).softDelete(templateId);
    }

    @Test
    @DisplayName("softDelete — throws NotFoundException when template does not exist")
    void softDelete_shouldThrow_whenTemplateNotFound() {
        UUID templateId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTemplateService.softDelete(projectId, templateId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job template not found");
    }

    @Test
    @DisplayName("softDelete — throws NotFoundException when template belongs to a different project")
    void softDelete_shouldThrow_whenTemplateBelongsToDifferentProject() {
        UUID templateId = UUID.randomUUID();
        JobTemplateModel otherTemplate = JobTemplateModel.builder()
                .id(templateId).projectId(UUID.randomUUID()).name("Other").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(otherTemplate));

        assertThatThrownBy(() -> jobTemplateService.softDelete(projectId, templateId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job template not found");
    }

    @Test
    @DisplayName("softDelete — member is forbidden")
    void softDelete_shouldThrow_whenMemberRole() {
        UUID templateId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobTemplateService.softDelete(projectId, templateId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    // --- recordUsage ---

    @Test
    @DisplayName("recordUsage — member can record template usage")
    void recordUsage_shouldIncrementOccurrenceCount_forMember() {
        UUID templateId = UUID.randomUUID();
        JobTemplateModel template = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("T1").occurrenceCount(3).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        jobTemplateService.recordUsage(projectId, templateId, memberId);

        verify(jobTemplateRepository).incrementOccurrenceCount(templateId);
    }

    @Test
    @DisplayName("recordUsage — throws NotFoundException when template does not exist")
    void recordUsage_shouldThrow_whenTemplateNotFound() {
        UUID templateId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTemplateService.recordUsage(projectId, templateId, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job template not found");
    }

    @Test
    @DisplayName("recordUsage — throws ForbiddenException when requester is not a member")
    void recordUsage_shouldThrow_whenNotMember() {
        UUID templateId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, nonMemberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTemplateService.recordUsage(projectId, templateId, nonMemberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- JobTemplateModel ---

    @Test
    @DisplayName("isDeleted returns true when deletedAt is set")
    void jobTemplateModel_isDeleted_returnsTrueWhenDeletedAtIsSet() {
        JobTemplateModel template = JobTemplateModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).name("T")
                .deletedAt(java.time.LocalDateTime.now()).build();

        assertThat(template.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("softDelete sets deletedAt")
    void jobTemplateModel_softDelete_setsDeletedAt() {
        JobTemplateModel template = JobTemplateModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).name("T").build();

        assertThat(template.isDeleted()).isFalse();
        template.softDelete();
        assertThat(template.isDeleted()).isTrue();
        assertThat(template.getDeletedAt()).isNotNull();
    }
}
