package com.ashu.shelflife.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code Authorization: Bearer <token>} header, verifies the access token, and
 * populates the {@code SecurityContext} with an {@link AuthenticatedUser} principal and a
 * {@code ROLE_<role>} authority. Requests without a valid token are left unauthenticated;
 * authorization (and the resulting 401/403) is handled downstream.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parse(token);
            AuthenticatedUser principal = new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class),
                    extractWarehouseIds(claims));

            var authority = new SimpleGrantedAuthority("ROLE_" + principal.role());
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(authority));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid / expired / malformed token: leave the context unauthenticated.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private List<Long> extractWarehouseIds(Claims claims) {
        Object raw = claims.get("warehouseIds");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(n -> ((Number) n).longValue())
                    .toList();
        }
        return List.of();
    }
}
