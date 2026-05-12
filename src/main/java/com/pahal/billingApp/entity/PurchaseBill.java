package com.pahal.billingApp.entity;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.enums.PurchaseStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "purchase_bills", indexes = {
        @Index(name = "idx_purchase_bills_tenant_date", columnList = "tenant_id, bill_date"),
        @Index(name = "idx_purchase_bills_tenant_supplier", columnList = "tenant_id, supplier_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_bills_tenant_supplier_bill_no", columnNames = {
                "tenant_id", "supplier_id", "bill_number"
        })
})
@Getter
@Setter
@ToString(exclude = { "supplier", "items", "payments" })
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PurchaseBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    private Double subTotalAmount;
    private Double discountAmount;
    private Double taxAmount;
    private Double totalAmount;
    private Double paidAmount;
    private Double dueAmount;

    @Enumerated(EnumType.STRING)
    private PurchaseStatus status = PurchaseStatus.ACTIVE;

    @Column(length = 1000)
    private String cancelReason;

    private LocalDateTime cancelledAt;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @OneToMany(mappedBy = "purchaseBill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<PurchaseBillItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "purchaseBill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private Set<PurchasePayment> payments = new LinkedHashSet<>();

    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = PurchaseStatus.ACTIVE;
        }
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
    }
}
