package com.opsclear.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public final class SecurityUtils {

    @lombok.Generated
    private SecurityUtils() {
    }

    /**
     * Extracts the caller's user ID from either JWT or API key authentication.
     * <p>
     * JWT auth: principal is a {@link org.springframework.security.oauth2.jwt.Jwt},
     * user ID comes from the {@code sub} claim.
     * API key auth: principal is the user ID as a plain string (set by {@link ApiKeyAuthFilter}).
     */
    public static UUID resolveUserId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return UUID.fromString(jwtAuth.getToken().getSubject());
        }
        return UUID.fromString((String) auth.getPrincipal());
    }
}
