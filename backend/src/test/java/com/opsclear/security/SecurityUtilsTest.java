package com.opsclear.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @Test
    @DisplayName("resolveUserId should extract UUID from JWT sub claim")
    void resolveUserId_shouldExtractUuid_fromJwtToken() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", "user@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        UUID resolved = SecurityUtils.resolveUserId(auth);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    @DisplayName("resolveUserId should extract UUID from API key authentication principal string")
    void resolveUserId_shouldExtractUuid_fromApiKeyAuth() {
        UUID userId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, Collections.emptyList());

        UUID resolved = SecurityUtils.resolveUserId(auth);

        assertThat(resolved).isEqualTo(userId);
    }
}
