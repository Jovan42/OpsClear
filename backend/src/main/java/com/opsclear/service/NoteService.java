package com.opsclear.service;

import com.opsclear.event.NoteAddedEvent;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.NoteModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteService {

    private final NoteRepository noteRepository;
    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NoteModel create(UUID projectId, UUID jobId, String content, UUID callerId) {
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        requireJobInProject(jobId, projectId);
        requireMember(projectId, callerId);

        NoteModel note = noteRepository.insert(jobId, callerId, content.strip());
        log.info("Note {} added to job {} in project {} by user {}", note.getId(), jobId, projectId, callerId);
        eventPublisher.publishEvent(new NoteAddedEvent(note.getId(), projectId, jobId, callerId));
        return note;
    }

    @Transactional(readOnly = true)
    public List<NoteModel> listByJob(UUID projectId, UUID jobId, UUID callerId) {
        requireProjectExistsById(projectId);
        JobModel job = requireJobInProject(jobId, projectId);
        ProjectMemberModel member = requireMember(projectId, callerId);
        requireCanViewJob(member, job, callerId);
        return noteRepository.findByJobId(jobId);
    }

    @Transactional(readOnly = true)
    public List<NoteModel> listByProject(UUID projectId, UUID callerId) {
        requireProjectExistsById(projectId);
        requireOwnerOrAdmin(projectId, callerId);
        return noteRepository.findByProjectId(projectId);
    }

    private ProjectModel requireProjectExists(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireProjectExistsById(UUID projectId) {
        requireProjectExists(projectId);
    }

    private void requireProjectNotCompleted(ProjectModel project) {
        if (project.isCompleted()) {
            throw new ConflictException(ErrorMessages.Project.COMPLETED_NO_MUTATIONS);
        }
    }

    private JobModel requireJobInProject(UUID jobId, UUID projectId) {
        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Job.NOT_FOUND);
        }
        return job;
    }

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    // JOB-411: same rule as JobService and JobHistoryService, a Member only sees the jobs assigned to them
    private void requireCanViewJob(ProjectMemberModel member, JobModel job, UUID callerId) {
        if (member.getRole() == ProjectMemberRole.MEMBER && !callerId.equals(job.getAssignedTo())) {
            throw new ForbiddenException(ErrorMessages.Job.ACCESS_DENIED_NOT_ASSIGNED);
        }
    }

    // JOB-411: the project-wide list spans every job, so a Member can't use it (it has no screen)
    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        if (requireMember(projectId, userId).getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }
}
