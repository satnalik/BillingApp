package com.pahal.billingApp.dto;

import lombok.Data;

@Data
public class TenantSettingsResponse {
    private String tenantId;
    private boolean gstEnabled;
    private double gstRate;
}

