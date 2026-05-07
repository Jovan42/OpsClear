package com.opsclear.aop;

import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RequiresAddonAspect {

    private final OrgSubscriptionRepository subscriptionRepository;
    private final OrganisationRepository organisationRepository;

    @Around("@annotation(requiresAddon)")
    public Object checkAddon(ProceedingJoinPoint pjp, RequiresAddon requiresAddon) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = SecurityUtils.resolveUserId(auth);

        UUID orgId = organisationRepository.findByMember(userId)
                .map(OrganisationModel::getId)
                .orElseThrow(() -> new ForbiddenException("No organisation found for this user"));

        if (subscriptionRepository.isInternal(orgId)) {
            log.debug("Skipping add-on check for internal org {}", orgId);
            return pjp.proceed();
        }

        if (!subscriptionRepository.hasAddon(orgId, requiresAddon.value().name())) {
            throw new ForbiddenException(
                    requiresAddon.value().name() + " add-on is required to access this feature");
        }

        return pjp.proceed();
    }
}
