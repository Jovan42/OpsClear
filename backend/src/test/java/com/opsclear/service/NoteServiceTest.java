package com.opsclear.service;

import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NoteModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock private NoteRepository noteRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private NoteService noteService;

    private UUID projectId;
    private UUID jobId;
    private UUID callerId;
    private ProjectModel project;
    private JobModel job;
    private ProjectMemberModel membership;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(noteRepository, jobRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        callerId = UUID.randomUUID();

        project = ProjectModel.builder()
                .id(projectId)
                .name("Test Project")
                .ownerId(callerId)
                .build();

        job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Install panel")
                .status(JobStatus.IN_PROGRESS)
                .build();

        membership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(callerId)
                .role(ProjectMemberRole.MEMBER)
                .build();
    }

    // --- create ---

    @Test
    @DisplayName("Any project member should be able to create a note")
    void create_shouldInsertNote_forMember() {
        NoteModel expected = NoteModel.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .authorId(callerId)
                .content("Client confirmed deadline.")
                .createdAt(Instant.now())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.of(membership));
        when(noteRepository.insert(jobId, callerId, "Client confirmed deadline.")).thenReturn(expected);

        NoteModel result = noteService.create(projectId, jobId, "Client confirmed deadline.", callerId);

        assertThat(result.getId()).isEqualTo(expected.getId());
        assertThat(result.getContent()).isEqualTo("Client confirmed deadline.");
        verify(noteRepository).insert(jobId, callerId, "Client confirmed deadline.");
    }

    @Test
    @DisplayName("create should strip leading and trailing whitespace from content")
    void create_shouldStripContent_beforeInsert() {
        NoteModel expected = NoteModel.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .authorId(callerId)
                .content("Trimmed content")
                .createdAt(Instant.now())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.of(membership));
        when(noteRepository.insert(jobId, callerId, "Trimmed content")).thenReturn(expected);

        noteService.create(projectId, jobId, "  Trimmed content  ", callerId);

        verify(noteRepository).insert(jobId, callerId, "Trimmed content");
    }

    @Test
    @DisplayName("create should throw NotFoundException when project does not exist")
    void create_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.create(projectId, jobId, "content", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("create should throw NotFoundException when job does not exist")
    void create_shouldThrow_whenJobNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.create(projectId, jobId, "content", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("create should throw NotFoundException when job belongs to a different project")
    void create_shouldThrow_whenJobInDifferentProject() {
        JobModel jobInOtherProject = JobModel.builder()
                .id(jobId)
                .projectId(UUID.randomUUID())
                .title("Other job")
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(jobInOtherProject));

        assertThatThrownBy(() -> noteService.create(projectId, jobId, "content", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("create should throw ForbiddenException when caller is not a project member")
    void create_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.create(projectId, jobId, "content", callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("create_shouldThrowConflictException_whenProjectIsCompleted")
    void create_shouldThrowConflict_whenProjectIsCompleted() {
        ProjectModel completed = ProjectModel.builder()
                .id(projectId).name("Test Project").ownerId(callerId).status(ProjectStatus.COMPLETED).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> noteService.create(projectId, jobId, "content", callerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This project is completed and no longer accepts changes");
    }

    // --- listByJob ---

    @Test
    @DisplayName("Any project member should be able to list notes for a job")
    void listByJob_shouldReturnNotes_forMember() {
        List<NoteModel> notes = List.of(
                NoteModel.builder().id(UUID.randomUUID()).jobId(jobId).content("First note").build(),
                NoteModel.builder().id(UUID.randomUUID()).jobId(jobId).content("Second note").build()
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.of(membership));
        when(noteRepository.findByJobId(jobId)).thenReturn(notes);

        List<NoteModel> result = noteService.listByJob(projectId, jobId, callerId);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getContent()).isEqualTo("First note");
        assertThat(result.get(1).getContent()).isEqualTo("Second note");
    }

    @Test
    @DisplayName("listByJob should throw NotFoundException when project does not exist")
    void listByJob_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.listByJob(projectId, jobId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("listByJob should throw NotFoundException when job does not exist")
    void listByJob_shouldThrow_whenJobNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.listByJob(projectId, jobId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("listByJob should throw ForbiddenException when caller is not a project member")
    void listByJob_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.listByJob(projectId, jobId, callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- listByProject ---

    @Test
    @DisplayName("Any project member should be able to list all notes in a project")
    void listByProject_shouldReturnNotes_forMember() {
        UUID jobId2 = UUID.randomUUID();
        List<NoteModel> notes = List.of(
                NoteModel.builder().id(UUID.randomUUID()).jobId(jobId).jobName("Install panel").content("Note A").build(),
                NoteModel.builder().id(UUID.randomUUID()).jobId(jobId2).jobName("Roof inspection").content("Note B").build()
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.of(membership));
        when(noteRepository.findByProjectId(projectId)).thenReturn(notes);

        List<NoteModel> result = noteService.listByProject(projectId, callerId);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getJobName()).isEqualTo("Install panel");
        assertThat(result.get(1).getJobName()).isEqualTo("Roof inspection");
    }

    @Test
    @DisplayName("listByProject should throw NotFoundException when project does not exist")
    void listByProject_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.listByProject(projectId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("listByProject should throw ForbiddenException when caller is not a project member")
    void listByProject_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.listByProject(projectId, callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }
}
