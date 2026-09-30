package com.orvexa.controlplane.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final String apiKey;
    private final String internalApiKey;

    public ApiKeyAuthenticationFilter(String apiKey, String internalApiKey) {
        this.apiKey = apiKey;
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = path.startsWith("/internal/")
                ? request.getHeader("X-Internal-API-Key")
                : request.getHeader("X-API-Key");

        String expectedKey = path.startsWith("/internal/") ? internalApiKey : apiKey;

        if (expectedKey == null || expectedKey.isBlank() || !expectedKey.equals(providedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Valid API key required\"}");
            return;
        }

        String principal = path.startsWith("/internal/") ? "internal-service" : "api-client";
        String authority = path.startsWith("/internal/") ? "ROLE_INTERNAL" : "ROLE_API";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                )
        );

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
