package com.pahal.billingApp.entity;

import com.pahal.billingApp.context.TenantContext;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Purpose: This class represents a single item in a store's inventory.
 *
 * The Multi-Tenant Magic: We use Hibernate's @FilterDef and @Filter annotations.
 * These work with the Aspect we just wrote to ensure that if a request comes in for "Store_A," Hibernate automatically appends WHERE tenant_id = 'Store_A' to the SQL query sent to Azure.
 */
@Entity
@Table(name = "products")
@Data // Lombok annotation to generate getters, setters, and toString
// 1. Define the filter name and the parameter it expects
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
// 2. Apply the filter logic to the tenant_id column
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double price;
    private Integer stockQuantity;
    private String category;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * JPA Lifecycle Hook
     * This automatically sets the tenantId from the context
     * whenever you save a new product!
     */
    @PrePersist
    public void onPrePersist() {
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
    }
}