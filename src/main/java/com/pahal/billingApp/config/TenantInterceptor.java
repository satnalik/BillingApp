package com.pahal.billingApp.config;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Purpose: This class intercepts every incoming HTTP request before it reaches your Controller.
 *
 * How it works: it looks for a specific header (like X-TenantID). If found, it tells the TenantContext to "remember" this ID for the rest of the operation.
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    public TenantInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Extract tenantId from the JWT claims
            String tenantId = jwtService.extractTenantId(token);

            if (tenantId != null) {
                TenantContext.setCurrentTenant(tenantId);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Clean up to prevent memory leaks in the thread pool
        TenantContext.clear();
    }
}
