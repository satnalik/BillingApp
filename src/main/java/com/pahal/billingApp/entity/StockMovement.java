package com.pahal.billingApp.entity;

import com.pahal.billingApp.enums.StockMovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;

/** Append-only through the API. Snapshots preserve the audit trail when product details change. */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_tenant_product", columnList = "tenant_id, product_id, id"),
        @Index(name = "idx_stock_movements_tenant_date", columnList = "tenant_id, created_at, id")
})
@Getter
@Setter
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    private String productName;
    @Column(length = 64)
    private String barcode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private StockMovementType type;
    @Column(nullable = false, length = 80)
    private String reason;
    @Column(length = 500)
    private String notes;
    @Column(nullable = false)
    private Double quantityBefore;
    @Column(nullable = false)
    private Double quantityChange;
    @Column(nullable = false)
    private Double quantityAfter;
    @Column(length = 240)
    private String reference;
    @Column(length = 240)
    private String sourceName;
    private Long referenceId;
    @Column(length = 160)
    private String actorUserId;
    @Column(nullable = false, length = 160)
    private String actorName;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
