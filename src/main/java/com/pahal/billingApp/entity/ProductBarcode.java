package com.pahal.billingApp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pahal.billingApp.context.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "product_barcodes", indexes = {
        @Index(name = "idx_product_barcodes_tenant_barcode", columnList = "tenant_id, barcode"),
        @Index(name = "idx_product_barcodes_tenant_product", columnList = "tenant_id, product_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_barcodes_tenant_barcode", columnNames = { "tenant_id", "barcode" })
})
@Getter
@Setter
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ProductBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Product product;

    @Column(nullable = false, length = 64)
    private String barcode;

    /**
     * Number of product units represented by one scan of this barcode.
     * Example: a carton barcode may add/sell 10 biscuit packs per scan.
     */
    @Column(name = "quantity_per_scan", nullable = false)
    private Double quantityPerScan = 1.0;

    @Column(name = "barcode_type", length = 32)
    private String barcodeType;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryBarcode;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    public void onPrePersist() {
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
        if (this.barcode != null) {
            this.barcode = this.barcode.trim();
        }
        if (this.quantityPerScan == null || this.quantityPerScan <= 0.0) {
            this.quantityPerScan = 1.0;
        }
    }
}
