package com.orvexa.controlplane.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orvexa.controlplane.config.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorrelationIdFilter correlationIdFilter,
            ObjectMapper objectMapper,
            @Value("${orvexa.security.api-key}") String apiKey,
            @Value("${orvexa.security.internal-api-key}") String internalApiKey
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(correlationIdFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(
                        new ApiKeyAuthenticationFilter(apiKey, internalApiKey, objectMapper),
                        CorrelationIdFilter.class
                )
                .build();
    }
}
