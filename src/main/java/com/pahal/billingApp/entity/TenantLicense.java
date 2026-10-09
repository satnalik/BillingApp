package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "tenant_licenses") @Getter @Setter
public class TenantLicense {
    @Id @Column(length = 100) private String tenantId;
    @Column(nullable = false, length = 36) private String deploymentId;
    @Column(nullable = false) private boolean legacyAccess;
    @Column(columnDefinition = "text") private String signedLicense;
    @Column(columnDefinition = "text") private String signedHistory;
    @Column(length = 100) private String acceptedLicenseId;
    @Column(nullable = false, columnDefinition = "bigint default 0") private long acceptedRevision;
    @Column(nullable = false, columnDefinition = "boolean default false") private boolean legacyMigrated;
    @Version private long version;
}
