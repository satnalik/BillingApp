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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_tenant_phone", columnList = "tenant_id, contact_number"),
        @Index(name = "idx_customers_tenant_name", columnList = "tenant_id, name")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_customers_tenant_contact", columnNames = { "tenant_id", "contact_number" })
})
@Getter
@Setter
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "contact_number", nullable = false, length = 32)
    private String contactNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
        if (this.name != null) {
            this.name = this.name.trim();
        }
        if (this.contactNumber != null) {
            this.contactNumber = this.contactNumber.trim();
        }
    }
}
