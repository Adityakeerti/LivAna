package com.livana.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS configuration — permits cross-origin requests from local dev origins.
 *
 * In production, replace the allowed origins with your actual frontend domain.
 * This CorsFilter runs BEFORE Spring Security's filter chain so the CORS
 * preflight (OPTIONS) requests are handled correctly.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow the test page (served from Spring Boot itself) + common dev origins
        config.setAllowedOrigins(List.of(
                "http://localhost:8080",   // test.html served by Spring Boot
                "http://localhost:3000",   // future React/Next.js frontend
                "http://localhost:5173"    // future Vite frontend
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
