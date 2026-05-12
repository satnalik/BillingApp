package com.pahal.billingApp.entity;

import com.pahal.billingApp.context.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "suppliers", indexes = {
        @Index(name = "idx_suppliers_tenant_name", columnList = "tenant_id, name"),
        @Index(name = "idx_suppliers_tenant_code", columnList = "tenant_id, supplier_code"),
        @Index(name = "idx_suppliers_tenant_active", columnList = "tenant_id, active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_suppliers_tenant_code", columnNames = { "tenant_id", "supplier_code" })
})
@Data
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "supplier_code", nullable = false, length = 32)
    private String supplierCode;

    private String contactPerson;
    private String phoneNumber;
    private String email;
    private String gstNumber;

    @Column(length = 1000)
    private String address;

    private boolean active = true;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    public void onPrePersist() {
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
        if (this.supplierCode != null) {
            this.supplierCode = this.supplierCode.trim().toUpperCase();
        }
    }
}
