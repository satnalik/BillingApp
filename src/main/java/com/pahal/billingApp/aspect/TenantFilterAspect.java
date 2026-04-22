package com.pahal.billingApp.aspect;

import com.pahal.billingApp.context.TenantContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Purpose: This is where your AOP (Aspect Oriented Programming) skills come in. This class automatically injects the tenant_id into your database queries.
 *
 * The Magic: Whenever you call a method in your Repository (like findAll), this aspect wakes up, looks at the TenantContext, and tells Hibernate to only fetch data for that specific store.
 */

@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    // This runs before any method in your repository package
    @Before("execution(* com.pahal.billingApp.repository.*.*(..))")
    public void enableTenantFilter() {
        Session session = entityManager.unwrap(Session.class);
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            // Activates the @Filter defined in the Entity
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
    }
}