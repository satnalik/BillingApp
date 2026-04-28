package com.pahal.billingApp.security;

import com.pahal.billingApp.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userId;

        String path = request.getServletPath();

        // 1. Skip filter for Auth endpoints
        if (path.contains("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Check for valid Authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            // Extract the userId (Subject) from the token
            userId = jwtService.extractClaim(jwt, io.jsonwebtoken.Claims::getSubject);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // IMPORTANT: Your CustomUserDetailsService must now load by userId
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userId);

                if (isTokenValid(jwt, userDetails)) {
                    // We use userDetails.getAuthorities() - Make sure your UserDetails
                    // implementation converts your Role enum to SimpleGrantedAuthority("ROLE_" + role)
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // As a tester, logging the specific error helps diagnose if the token expired or signature failed
            logger.error("Could not set user authentication in security context", e);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getSubject);
        return (username.equals(userDetails.getUsername()));
    }
}