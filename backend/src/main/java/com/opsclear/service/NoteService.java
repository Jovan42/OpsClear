package com.opsclear.service;

import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.NoteModel;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public NoteModel create(UUID projectId, UUID jobId, String content, UUID callerId) {
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        requireJobInProject(jobId, projectId);
        requireMember(projectId, callerId);

        NoteModel note = noteRepository.insert(jobId, callerId, content.strip());
        log.info("Note {} added to job {} in project {} by user {}", note.getId(), jobId, projectId, callerId);
        return note;
    }

    @Transactional(readOnly = true)
    public List<NoteModel> listByJob(UUID projectId, UUID jobId, UUID callerId) {
        requireProjectExistsById(projectId);
        requireJobInProject(jobId, projectId);
        requireMember(projectId, callerId);
        return noteRepository.findByJobId(jobId);
    }

    @Transactional(readOnly = true)
    public List<NoteModel> listByProject(UUID projectId, UUID callerId) {
        requireProjectExistsById(projectId);
        requireMember(projectId, callerId);
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

    private void requireJobInProject(UUID jobId, UUID projectId) {
        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Job.NOT_FOUND);
        }
    }

    private void requireMember(UUID projectId, UUID userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }
}
