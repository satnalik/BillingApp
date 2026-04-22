package com.pahal.billingApp.context;

/**
 * Purpose: This class uses a ThreadLocal variable to store the tenantId for the duration of a single web request.
 *
 * Why it's needed: Since Spring Boot handles many users at once, we need a thread-safe way to ensure Store A's ID doesn't accidentally leak into Store B's processing.
 */
public class TenantContext {
    // ThreadLocal ensures each request thread has its own isolated tenantId
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
