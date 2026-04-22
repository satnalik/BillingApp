package com.pahal.billingApp.context;

import com.pahal.billingApp.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService; // A service to decode the token

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // Extract the tenantId we hid in the token during login
            String tenantId = jwtService.extractTenantId(token);

            if (tenantId != null) {
                // Automatically set the context! No need for manual X-TenantID headers anymore.
                TenantContext.setCurrentTenant(tenantId);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear the context after the request finishes
            TenantContext.clear();
        }
    }
}