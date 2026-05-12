package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.TenantSettingsResponse;
import com.pahal.billingApp.dto.UpdateTenantSettingsRequest;
import com.pahal.billingApp.entity.TenantSettings;
import com.pahal.billingApp.service.TenantSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/settings")
@Tag(name = "Tenant Settings API", description = "Endpoints for per-tenant configuration like GST toggles")
public class TenantSettingsController {

    @Autowired
    private TenantSettingsService tenantSettingsService;

    @Operation(summary = "Get Current Tenant Settings", description = "Returns GST settings for the current tenant (from JWT).")
    @GetMapping
    public ResponseEntity<TenantSettingsResponse> get() {
        TenantSettings s = tenantSettingsService.getOrCreateCurrentTenantSettings();
        return ResponseEntity.ok(toResponse(s));
    }

    @Operation(summary = "Update Current Tenant Settings", description = "Updates GST settings for the current tenant (from JWT).")
    @PutMapping
    public ResponseEntity<TenantSettingsResponse> update(@RequestBody UpdateTenantSettingsRequest request) {
        TenantSettings s = tenantSettingsService.updateCurrentTenantSettings(
                request != null ? request.getGstEnabled() : null,
                request != null ? request.getGstRate() : null
        );
        return ResponseEntity.ok(toResponse(s));
    }

    private static TenantSettingsResponse toResponse(TenantSettings s) {
        TenantSettingsResponse r = new TenantSettingsResponse();
        r.setTenantId(s.getTenantId());
        r.setGstEnabled(s.isGstEnabled());
        r.setGstRate(s.getGstRate());
        return r;
    }
}

