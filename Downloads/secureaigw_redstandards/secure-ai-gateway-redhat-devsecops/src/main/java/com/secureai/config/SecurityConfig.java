package com.secureai.config;

import com.secureai.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the application.
 * Implements OWASP security best practices.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection disabled for stateless API (JWT-based)
                // In production, consider using CSRF tokens for state-changing operations
                .csrf(AbstractHttpConfigurer::disable)
                
                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Session management - stateless for JWT
                .sessionManagement(session -> 
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/auth/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api-docs/**"
                        ).permitAll()
                        // Protected endpoints - require authentication
                        .requestMatchers("/api/**").authenticated()
                        // Actuator endpoints - require authentication
                        .requestMatchers("/actuator/**").authenticated()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                
                // Security headers
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> 
                                csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss.headerValue("1; mode=block"))
                        .contentTypeOptions(contentType -> contentType.disable())
                );

        // Add JWT filter before UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration for cross-origin requests.
     * In production, restrict to specific origins.
     *
     * @return CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // In production, replace with specific allowed origins
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * Password encoder bean using BCrypt.
     * BCrypt is recommended by OWASP for password hashing.
     *
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength of 12 rounds
    }
}
