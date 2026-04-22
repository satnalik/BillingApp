package com.pahal.billingApp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Purpose: This tells Spring Framework that our TenantInterceptor actually exists and should be applied to every API endpoint.
 */

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    // Constructor injection is preferred over @Autowired on fields
    public WebMvcConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**") // Apply to all your API endpoints
                .excludePathPatterns("/api/auth/**"); // EXCLUDE login/register so they don't need a Tenant ID
    }
}
