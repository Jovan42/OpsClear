package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobLinkModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.ProjectLinkModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobLinkRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectLinkRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkService {

    private static final Set<String> DISALLOWED_SCHEMES = Set.of("javascript", "data", "vbscript");

    private final JobLinkRepository jobLinkRepository;
    private final ProjectLinkRepository projectLinkRepository;
    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    // --- Job links ---

    @Transactional
    public JobLinkModel createForJob(UUID projectId, UUID jobId, String url, String label, UUID callerId) {
        requireProject(projectId);
        requireJobInProject(jobId, projectId);
        requireMember(projectId, callerId);
        validateUrl(url);

        JobLinkModel link = JobLinkModel.builder()
                .jobId(jobId)
                .url(url.strip())
                .label(label != null ? label.strip() : null)
                .createdBy(callerId)
                .build();
        JobLinkModel saved = jobLinkRepository.save(link);
        log.info("Added link {} to job {} in project {} by user {}", saved.getId(), jobId, projectId, callerId);
        return saved;
    }

    @Transactional
    public JobLinkModel updateJobLink(UUID projectId, UUID jobId, UUID linkId,
                                       String url, String label, UUID callerId) {
        requireProject(projectId);
        requireJobInProject(jobId, projectId);
        requireOwnerOrAdmin(projectId, callerId);
        validateUrl(url);

        JobLinkModel link = requireJobLink(linkId, jobId);
        link.setUrl(url.strip());
        link.setLabel(label != null ? label.strip() : null);
        JobLinkModel updated = jobLinkRepository.save(link);
        log.info("Updated link {} on job {} in project {} by user {}", linkId, jobId, projectId, callerId);
        return updated;
    }

    @Transactional
    public void deleteJobLink(UUID projectId, UUID jobId, UUID linkId, UUID callerId) {
        requireProject(projectId);
        requireJobInProject(jobId, projectId);
        requireOwnerOrAdmin(projectId, callerId);
        requireJobLink(linkId, jobId);

        jobLinkRepository.deleteById(linkId);
        log.info("Deleted link {} from job {} in project {} by user {}", linkId, jobId, projectId, callerId);
    }

    // --- Project links ---

    @Transactional
    public ProjectLinkModel createForProject(UUID projectId, String url, String label, UUID callerId) {
        requireProject(projectId);
        requireMember(projectId, callerId);
        validateUrl(url);

        ProjectLinkModel link = ProjectLinkModel.builder()
                .projectId(projectId)
                .url(url.strip())
                .label(label != null ? label.strip() : null)
                .createdBy(callerId)
                .build();
        ProjectLinkModel saved = projectLinkRepository.save(link);
        log.info("Added link {} to project {} by user {}", saved.getId(), projectId, callerId);
        return saved;
    }

    @Transactional
    public ProjectLinkModel updateProjectLink(UUID projectId, UUID linkId, String url, String label, UUID callerId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, callerId);
        validateUrl(url);

        ProjectLinkModel link = requireProjectLink(linkId, projectId);
        link.setUrl(url.strip());
        link.setLabel(label != null ? label.strip() : null);
        ProjectLinkModel updated = projectLinkRepository.save(link);
        log.info("Updated link {} on project {} by user {}", linkId, projectId, callerId);
        return updated;
    }

    @Transactional
    public void deleteProjectLink(UUID projectId, UUID linkId, UUID callerId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, callerId);
        requireProjectLink(linkId, projectId);

        projectLinkRepository.deleteById(linkId);
        log.info("Deleted link {} from project {} by user {}", linkId, projectId, callerId);
    }

    // --- Guards ---

    private void requireProject(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
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

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    private JobLinkModel requireJobLink(UUID linkId, UUID jobId) {
        return jobLinkRepository.findById(linkId)
                .filter(l -> l.getJobId().equals(jobId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Link.NOT_FOUND));
    }

    private ProjectLinkModel requireProjectLink(UUID linkId, UUID projectId) {
        return projectLinkRepository.findById(linkId)
                .filter(l -> l.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Link.NOT_FOUND));
    }

    private void validateUrl(String url) {
        try {
            String scheme = new URI(url.strip()).getScheme();
            if (scheme == null || DISALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
                throw new BadRequestException(ErrorMessages.Link.INVALID_URL);
            }
        } catch (URISyntaxException | NullPointerException e) {
            throw new BadRequestException(ErrorMessages.Link.INVALID_URL);
        }
    }
}
