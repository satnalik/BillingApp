package com.pahal.billingApp.dto;

import lombok.Data;

@Data
public class UpdateTenantSettingsRequest {
    /**
     * When false, no GST is added to bills for this tenant.
     */
    private Boolean gstEnabled;

    /**
     * GST rate as fraction. Example: 0.18 for 18%.
     */
    private Double gstRate;
}

