package com.livana.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livana.backend.auth.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JWT authentication filter — runs once per request, before Spring Security's
 * authorization checks.
 *
 * Contract:
 *   1. Extract Bearer token from Authorization header
 *   2. Validate signature + expiry via JwtService
 *   3. Set userId as principal in the SecurityContext
 *   4. On any JwtException → immediately return 401 JSON (do not propagate to filter chain)
 *
 * The principal stored in the SecurityContext is the userId (Long),
 * accessible via @AuthenticationPrincipal in controllers.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService   jwtService;
    private final ObjectMapper objectMapper;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // No Authorization header → pass through (SecurityConfig will block if endpoint requires auth)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.validateToken(token);

            Long   userId = jwtService.extractUserId(claims);
            String role   = jwtService.extractRole(claims);

            // Build Spring Security authentication object
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,   // principal — accessed via @AuthenticationPrincipal
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException e) {
            // Token is invalid or expired — write 401 response directly and stop the chain
            writeUnauthorized(response, "Invalid or expired JWT: " + e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    401,
                "message",   message
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
