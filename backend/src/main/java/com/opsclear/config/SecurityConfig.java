package com.opsclear.config;

import com.opsclear.security.ApiKeyAuthFilter;
import com.opsclear.security.UserSyncFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final UserSyncFilter userSyncFilter;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Api-Key"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/subscriptions/catalog").permitAll()
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterBefore(apiKeyAuthFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(userSyncFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
