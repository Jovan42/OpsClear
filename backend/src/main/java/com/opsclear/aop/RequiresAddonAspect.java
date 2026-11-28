package com.opsclear.aop;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.AddonAccessChecker;
import lombok.RequiredArgsConstructor;
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
 * Applies the add-on rule from {@link AddonAccessChecker} to every {@code @RequiresAddon} endpoint: internal and trial
 * orgs pass (ADR-0067), a paid org needs the add-on in its real, webhook-confirmed Paddle subscription (JOB-200: a
 * staged tier/add-on pick is not itself access, mirrored by {@code addonAccess.ts} on the frontend). Also enforces
 * ADR-0044's PAST_DUE gating: reads stay available, writes are blocked until payment recovers. Detects read vs. write
 * by inspecting the intercepted method's own {@code @GetMapping}: every {@code @RequiresAddon} method carries exactly
 * one Spring mapping annotation, so this needs no separate read/write metadata.
 */
@Aspect
@Component
@RequiredArgsConstructor
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

        AddonCode addon = requiresAddon.value();
        if (!addonAccessChecker.hasAddonAccess(orgId, addon)) {
            throw new ForbiddenException(addon.name() + " add-on is required to access this feature");
        }

        // The org can read the add-on, so a write it can't make is the PAST_DUE case.
        if (!isReadOnlyRequest(pjp) && !addonAccessChecker.isAddonUsableForWrites(orgId, addon)) {
            throw new ForbiddenException(ErrorMessages.Paddle.SUBSCRIPTION_PAST_DUE);
        }

        return pjp.proceed();
    }

    private boolean isReadOnlyRequest(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        return method.isAnnotationPresent(GetMapping.class);
    }
}
