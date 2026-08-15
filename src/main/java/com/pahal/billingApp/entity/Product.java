package com.pahal.billingApp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.enums.ItemType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Purpose: This class represents a single item in a store's inventory.
 *
 * The Multi-Tenant Magic: We use Hibernate's @FilterDef and @Filter
 * annotations.
 * These work with the Aspect we just wrote to ensure that if a request comes in
 * for "Store_A," Hibernate automatically appends WHERE tenant_id = 'Store_A' to
 * the SQL query sent to Azure.
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_tenant_name", columnList = "tenant_id, name"),
        @Index(name = "idx_products_tenant_barcode", columnList = "tenant_id, barcode"),
        @Index(name = "idx_products_tenant_supplier", columnList = "tenant_id, supplier_id")
},
        // Unique constraints for the tenant_id and barcode combination
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_products_tenant_barcode", columnNames = { "tenant_id", "barcode" })
        })
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

    /**
     * Barcode printed on the product pack (EAN-13/UPC/Code128 etc.).
     * Unique per tenant.
     */
    @Column(length = 64)
    private String barcode;

    /**
     * Legacy field used as selling price in existing code.
     * Prefer {@link #sellingPrice} for new features.
     */
    private Double price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Supplier supplier;

    private String supplierName;
    private Double costPrice;
    private Double landingPrice;
    private Double mrp;
    private Double sellingPrice;
    private Double stockQuantity;
    private String category;
    private String hsnCode;
    private Double gstRate;

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
