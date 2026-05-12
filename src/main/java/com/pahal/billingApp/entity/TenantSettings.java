package com.pahal.billingApp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
public class TenantSettings {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "gst_enabled", nullable = false)
    private boolean gstEnabled = true;

    /**
     * GST rate as a fraction. Example: 0.18 for 18%.
     */
    @Column(name = "gst_rate", nullable = false)
    private double gstRate = 0.18;
}

