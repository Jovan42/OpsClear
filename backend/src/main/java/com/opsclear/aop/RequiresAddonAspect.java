package com.opsclear.aop;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.AddonAccessChecker;
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
 * Requires real, webhook-confirmed Paddle billing before honoring the staged
 * {@code hasAddon()} selection (JOB-200: a staged tier/add-on pick is not itself
 * access — see {@code hasRealPaddleBilling()} on the frontend for the mirrored check).
 * Also enforces ADR-0044's PAST_DUE gating for every {@code @RequiresAddon}-protected
 * endpoint: reads stay available, writes are blocked until payment recovers. Detects
 * read vs. write by inspecting the intercepted method's own {@code @GetMapping} —
 * every {@code @RequiresAddon} method carries exactly one Spring mapping annotation,
 * so this needs no separate read/write metadata. Skipped entirely for internal orgs.
 * The rule itself lives in {@link AddonAccessChecker}, shared with service-level checks.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RequiresAddonAspect {

    private final AddonAccessChecker addonAccessChecker;
    private final OrganisationRepository organisationRepository;

    @Around("@annotation(requiresAddon)")
    public Object checkAddon(ProceedingJoinPoint pjp, RequiresAddon requiresAddon) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = SecurityUtils.resolveUserId(auth);

        UUID orgId = organisationRepository.findByMember(userId)
                .map(OrganisationModel::getId)
                .orElseThrow(() -> new ForbiddenException("No organisation found for this user"));

        if (addonAccessChecker.isInternal(orgId)) {
            log.debug("Skipping add-on check for internal org {}", orgId);
            return pjp.proceed();
        }

        if (!addonAccessChecker.hasPaidAddon(orgId, requiresAddon.value())) {
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

        if (addonAccessChecker.isPastDue(orgId)) {
            throw new ForbiddenException(ErrorMessages.Paddle.SUBSCRIPTION_PAST_DUE);
        }
    }

    private boolean isReadOnlyRequest(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        return method.isAnnotationPresent(GetMapping.class);
    }
}
