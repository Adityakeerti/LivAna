package com.livana.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.Map;

/**
 * Spring Security configuration for LivAna.
 *
 * Policy : STATELESS — no sessions, no cookies, JWT-only.
 * CSRF   : disabled — JWT on stateless API is not vulnerable to CSRF.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter   jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper    objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthFilter   = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.objectMapper    = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                // ── Static resources ─────────────────────────────────────────────
                .requestMatchers("/", "/*.html", "/css/**", "/js/**",
                                  "/images/**", "/favicon.ico", "/webjars/**").permitAll()

                // ── CORS preflight — must be permitAll ───────────────────────────
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Public auth endpoints ────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/auth/google").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()

                // ── Actuator ─────────────────────────────────────────────────────
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // ── Everything else requires a valid JWT ─────────────────────────
                .anyRequest().authenticated()
            )

            // Custom 401 JSON response — no HTML leakage
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    Map<String, Object> body = Map.of(
                            "timestamp", Instant.now().toString(),
                            "status",    401,
                            "message",   "Authentication required — provide a valid Bearer token"
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
            )

            // RateLimit runs first, then JWT validation
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter,   UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
