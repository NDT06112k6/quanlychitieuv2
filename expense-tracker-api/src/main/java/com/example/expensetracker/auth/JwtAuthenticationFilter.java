package com.example.expensetracker.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, AuthConstants.BEARER_PREFIX, 0,
                AuthConstants.BEARER_PREFIX.length())) {
            authenticate(header.substring(AuthConstants.BEARER_PREFIX.length()).strip());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            AuthPrincipal principal = jwtService.validateToken(token);
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(AuthConstants.ROLE_PREFIX + principal.role().name());
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, List.of(authority));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException exception) {
            // Invalid Bearer credentials remain anonymous; authorization decides whether this route requires login.
            SecurityContextHolder.clearContext();
        }
    }
}
