package com.opsclear.aop;

import com.opsclear.exception.ErrorMessages;
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
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Also enforces ADR-0044's PAST_DUE gating for every {@code @RequiresAddon}-protected
 * endpoint: reads stay available, writes are blocked until payment recovers. Detects
 * read vs. write by inspecting the intercepted method's own {@code @GetMapping} —
 * every {@code @RequiresAddon} method carries exactly one Spring mapping annotation,
 * so this needs no separate read/write metadata. Skipped entirely for internal orgs
 * (same as the add-on check) and when {@code subscription_status} is null — the org
 * hasn't completed its first Paddle checkout yet, so no restriction applies.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RequiresAddonAspect {

    private static final String PAST_DUE = "PAST_DUE";

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

        requireNotPastDueForWrite(pjp, orgId);

        return pjp.proceed();
    }

    private void requireNotPastDueForWrite(ProceedingJoinPoint pjp, UUID orgId) {
        if (isReadOnlyRequest(pjp)) {
            return;
        }

        boolean pastDue = subscriptionRepository.findSubscriptionStatus(orgId)
                .filter(PAST_DUE::equals)
                .isPresent();
        if (pastDue) {
            throw new ForbiddenException(ErrorMessages.Paddle.SUBSCRIPTION_PAST_DUE);
        }
    }

    private boolean isReadOnlyRequest(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        return method.isAnnotationPresent(GetMapping.class);
    }
}
