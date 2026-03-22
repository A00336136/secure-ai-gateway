package com.secureai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate when valid token is provided")
    void shouldAuthenticateWhenValidToken() throws ServletException, IOException {
        String token = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(token)).thenReturn("user1");
        when(jwtUtil.getRoleFromToken(token)).thenReturn("ADMIN");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user1");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should use ROLE_USER when role is missing")
    void shouldDefaultToRoleUserWhenRoleMissing() throws ServletException, IOException {
        String token = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(token)).thenReturn("user2");
        when(jwtUtil.getRoleFromToken(token)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        verify(filterChain).doFilter(request, response);
    }

    @ParameterizedTest(name = "header=''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"Basic dXNlcjpwYXNz", "Token abc123", "bearer lowercase"})
    @DisplayName("Should not authenticate when Authorization header is missing or invalid")
    void shouldNotAuthenticateWhenHeaderInvalid(String headerValue) throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(headerValue);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not authenticate when token is invalid")
    void shouldNotAuthenticateWhenTokenInvalid() throws ServletException, IOException {
        String token = "invalid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should default to ROLE_USER when role is blank string")
    void shouldDefaultToRoleUserWhenRoleIsBlank() throws ServletException, IOException {
        String token = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(token)).thenReturn("user3");
        when(jwtUtil.getRoleFromToken(token)).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should set remote address as authentication details")
    void shouldSetRemoteAddressAsDetails() throws ServletException, IOException {
        String token = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(token)).thenReturn("user4");
        when(jwtUtil.getRoleFromToken(token)).thenReturn("USER");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("Should handle Bearer prefix with extra spaces in token")
    void shouldHandleBearerWithSpaces() throws ServletException, IOException {
        String token = "  valid-token  ";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("valid-token")).thenReturn("user5");
        when(jwtUtil.getRoleFromToken("valid-token")).thenReturn("ADMIN");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
