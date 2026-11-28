package com.opsclear.config;

import com.opsclear.security.OrgLockInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC setup: the trial and lock-out properties, and the lock-out interceptor on {@code /api/**} (ADR-0067).
 * The allowlist lives in {@link OrgLockInterceptor} itself, since two of its entries are GET-only.
 */
@Configuration
@EnableConfigurationProperties({TrialProperties.class, LockoutProperties.class})
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OrgLockInterceptor orgLockInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(orgLockInterceptor).addPathPatterns("/api/**");
    }
}
