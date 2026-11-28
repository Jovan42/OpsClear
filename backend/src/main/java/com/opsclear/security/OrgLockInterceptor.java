package com.opsclear.security;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.OrgLockedException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.service.OrgAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The paywall lock-out (ADR-0067): every {@code /api/**} call from a {@code LOCKED} org gets 402 {@code OrgLocked},
 * except the {@link #ALLOWLIST} (profile, preferences, the org itself, subscription and Paddle checkout, credits,
 * catalog, health, webhooks, the public invite page, public sign-up, super-admin). It fails closed: a new endpoint is
 * blocked for locked orgs unless it's added here.
 * <ul>
 *   <li>The caller's org is resolved from the user ({@code findByMember}, one org per user). An API key acts as its
 *       user, so a key of a locked org is blocked too. The lookup is cached per request.</li>
 *   <li>Unauthenticated calls and users without an org pass (onboarding needs that).</li>
 *   <li>While the lock-out switch is off ({@code opsclear.lockout.enabled=false}), nothing is blocked.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OrgLockInterceptor implements HandlerInterceptor {

    static final String ORG_ID_ATTRIBUTE = OrgLockInterceptor.class.getName() + ".orgId";

    private static final List<AllowedRoute> ALLOWLIST = List.of(
            AllowedRoute.any("/api/users/**"),
            AllowedRoute.any("/api/user/preferences"),
            AllowedRoute.any("/api/organisations/mine"),
            AllowedRoute.get("/api/organisations/{id}"),
            AllowedRoute.any("/api/organisations/{orgId}/subscription/**"),
            AllowedRoute.any("/api/organisations/{orgId}/credits/**"),
            AllowedRoute.any("/api/subscriptions/catalog"),
            AllowedRoute.any("/api/health"),
            AllowedRoute.any("/api/webhooks/**"),
            AllowedRoute.get("/api/invites/{token}"),
            AllowedRoute.any("/api/public/**"),
            AllowedRoute.any("/api/super-admin/**"));

    private final OrganisationRepository organisationRepository;
    private final OrgAccessService orgAccessService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!orgAccessService.isLockoutEnabled() || isAllowlisted(request)) {
            return true;
        }
        Optional<UUID> orgId = resolveCallerOrg(request);
        if (orgId.isPresent() && orgAccessService.isLockedOut(orgId.get())) {
            throw new OrgLockedException(ErrorMessages.Organisation.LOCKED);
        }
        return true;
    }

    static boolean isAllowlisted(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        PathContainer path = PathContainer.parsePath(
                request.getRequestURI().substring(request.getContextPath().length()));
        return ALLOWLIST.stream().anyMatch(route -> route.matches(request.getMethod(), path));
    }

    // Cached on the request, so a re-dispatch (async, error) doesn't look the org up again.
    @SuppressWarnings("unchecked")
    private Optional<UUID> resolveCallerOrg(HttpServletRequest request) {
        Object cached = request.getAttribute(ORG_ID_ATTRIBUTE);
        if (cached != null) {
            return (Optional<UUID>) cached;
        }
        Optional<UUID> orgId = callerUserId()
                .flatMap(organisationRepository::findByMember)
                .map(OrganisationModel::getId);
        request.setAttribute(ORG_ID_ATTRIBUTE, orgId);
        return orgId;
    }

    private static Optional<UUID> callerUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(SecurityUtils.resolveUserId(auth));
    }

    private record AllowedRoute(HttpMethod method, PathPattern pattern) {

        static AllowedRoute any(String pattern) {
            return new AllowedRoute(null, PathPatternParser.defaultInstance.parse(pattern));
        }

        static AllowedRoute get(String pattern) {
            return new AllowedRoute(HttpMethod.GET, PathPatternParser.defaultInstance.parse(pattern));
        }

        boolean matches(String requestMethod, PathContainer path) {
            return (method == null || method.matches(requestMethod)) && pattern.matches(path);
        }
    }
}
