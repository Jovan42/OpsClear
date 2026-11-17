package com.opsclear.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ErrorResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    static final String RATE_LIMIT_ERROR = "Too Many Requests";

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

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

        ApiKeyRateLimiter.Decision decision = rateLimiter.tryConsume(apiKey.getId(), apiKey.getUserId());
        if (!decision.allowed()) {
            if (decision.shouldWarn()) {
                log.warn("Rate limit exceeded for API key '{}' (user {}); further rejections are logged at most "
                        + "once a minute", apiKey.getKeyPrefix(), apiKey.getUserId());
            }
            writeTooManyRequests(response, decision.retryAfterSeconds());
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

    // @RestControllerAdvice doesn't apply inside filters, so the 429 is written here (ADR-0058).
    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String message = ErrorMessages.ApiKey.RATE_LIMIT_EXCEEDED.formatted(retryAfterSeconds);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(RATE_LIMIT_ERROR, message));
    }
}
