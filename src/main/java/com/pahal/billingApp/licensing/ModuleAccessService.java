package com.pahal.billingApp.licensing;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.security.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ModuleAccessService {
    private final TenantLicenseService licenses;
    public ModuleAccessService(TenantLicenseService licenses) { this.licenses = licenses; }
    public String tenant() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails user)) throw new LicenseAccessException("AUTH_REQUIRED", "Sign in to continue.");
        if (TenantContext.getCurrentTenant() != null && !user.getTenantId().equals(TenantContext.getCurrentTenant()))
            throw new LicenseAccessException("TENANT_MISMATCH", "Tenant identity mismatch.");
        return user.getTenantId();
    }
    public boolean manager() { return role("ROLE_ADMIN") || role("ROLE_MANAGER"); }
    public boolean role(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }
    public void require(Feature feature) {
        var current = licenses.snapshot(tenant()); TenantLicenseService.requireOperational(current);
        if (!current.features().contains(feature)) throw new LicenseAccessException("MODULE_NOT_LICENSED", "Your store license does not include " + feature.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ". Contact the application owner.");
        if (!Feature.CORE.contains(feature) && !manager()) throw new LicenseAccessException("ROLE_REQUIRED", "Only managers and administrators can use this module.");
    }
    public boolean can(Feature feature, TenantLicenseService.Snapshot current) {
        return current.operational() && current.features().contains(feature) && (Feature.CORE.contains(feature) || manager());
    }
    public Map<String, Object> capabilities() {
        var current = licenses.snapshot(tenant()); var actions = new LinkedHashMap<String, Boolean>();
        for (Feature feature : Feature.values()) actions.put(feature.name(), can(feature, current));
        actions.put("bill.create", can(Feature.BILLING, current));
        actions.put("bill.history.read", true); actions.put("bill.settle", true);
        actions.put("products.manage", current.operational() && manager());
        actions.put("products.read", manager());
        actions.put("purchase.create", can(Feature.PURCHASES, current));
        actions.put("purchase.history.read", manager()); actions.put("purchase.settle", manager());
        actions.put("settings.manage", role("ROLE_ADMIN")); actions.put("users.manage", role("ROLE_ADMIN"));
        actions.put("license.manage", role("ROLE_ADMIN")); actions.put("shift.use", true);
        actions.put("reports.basic", manager());
        actions.put("gst.settings", role("ROLE_ADMIN"));
        actions.put("gst.history", manager() && licenses.everLicensed(tenant(), Feature.GST_ACCOUNTING));
        actions.put("inventory.history", manager() && licenses.everLicensed(tenant(), Feature.STOCK_ADJUSTMENTS));
        actions.put("purchase.history.read", manager() && licenses.everLicensed(tenant(), Feature.PURCHASES));
        actions.put("supplier.statement.read", manager() && licenses.everLicensed(tenant(), Feature.SUPPLIER_STATEMENTS));
        actions.put("cash.history", manager() && licenses.everLicensed(tenant(), Feature.CASH_RECONCILIATION));
        return Map.of("license", current, "allowedActions", actions);
    }
    public void requireHistory(Feature feature) {
        if (!manager() || !licenses.everLicensed(tenant(), feature))
            throw new LicenseAccessException("MODULE_NOT_LICENSED", "This store has no license history for " + feature.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
    }
}
