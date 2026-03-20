package com.opsclear.security;

import com.opsclear.model.ApiKeyModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApiKeyRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-Api-Key";

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);

        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String keyHash = ApiKeyService.sha256(rawKey);

        Optional<ApiKeyModel> keyOpt = apiKeyRepository.findByKeyHash(keyHash);

        if (keyOpt.isEmpty() || !keyOpt.get().isActive()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            return;
        }

        ApiKeyModel apiKey = keyOpt.get();

        Optional<UserModel> userOpt = userRepository.findById(apiKey.getUserId());
        if (userOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "API key user not found");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                apiKey.getUserId().toString(),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        apiKeyRepository.updateLastUsedAt(apiKey.getId());

        log.debug("Authenticated request via API key '{}' for user {}", apiKey.getKeyPrefix(), apiKey.getUserId());

        filterChain.doFilter(request, response);
    }
}
