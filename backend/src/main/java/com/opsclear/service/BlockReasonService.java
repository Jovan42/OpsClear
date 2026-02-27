package com.opsclear.service;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.BlockReasonRepository;
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
public class BlockReasonService {

    private final BlockReasonRepository blockReasonRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public List<BlockReasonModel> listActive(UUID projectId, UUID requesterId) {
        requireProjectExists(projectId);
        requireMember(projectId, requesterId);
        return blockReasonRepository.findActiveByProjectId(projectId);
    }

    @Transactional
    public BlockReasonModel findOrCreate(UUID projectId, String reason) {
        return blockReasonRepository.findOrCreate(projectId, reason.strip());
    }

    @Transactional
    public void softDelete(UUID projectId, UUID reasonId, UUID requesterId) {
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        blockReasonRepository.findByIdAndDeletedAtIsNull(reasonId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.BlockReason.NOT_FOUND));
        blockReasonRepository.softDelete(reasonId);
        log.info("Soft-deleted block reason {} from project {} by user {}", reasonId, projectId, requesterId);
    }

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
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
}
