package com.pahal.billingApp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProvisionTenantOwnerRequest {
    private String userId;
    private String password;
    private String name;
    private String licenseFile;
}
