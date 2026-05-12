package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, String> {
}

