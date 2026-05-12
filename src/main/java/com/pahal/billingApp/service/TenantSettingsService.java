package com.pahal.billingApp.service;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.TenantSettings;
import com.pahal.billingApp.repository.TenantSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSettingsService {

    @Autowired
    private TenantSettingsRepository tenantSettingsRepository;

    @Transactional(readOnly = true)
    public TenantSettings getOrCreateCurrentTenantSettings() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new RuntimeException("Tenant is required");
        }

        return tenantSettingsRepository.findById(tenantId)
                .orElseGet(() -> {
                    TenantSettings s = new TenantSettings();
                    s.setTenantId(tenantId);
                    return tenantSettingsRepository.save(s);
                });
    }

    @Transactional
    public TenantSettings updateCurrentTenantSettings(Boolean gstEnabled, Double gstRate) {
        TenantSettings s = getOrCreateCurrentTenantSettings();

        if (gstEnabled != null) {
            s.setGstEnabled(gstEnabled);
        }
        if (gstRate != null) {
            if (gstRate < 0.0 || gstRate > 1.0) {
                throw new RuntimeException("gstRate must be between 0.0 and 1.0 (example: 0.18)");
            }
            s.setGstRate(gstRate);
        }

        return tenantSettingsRepository.save(s);
    }
}

