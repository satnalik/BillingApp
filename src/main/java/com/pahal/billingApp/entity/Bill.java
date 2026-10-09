package com.pahal.billingApp.entity;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.enums.BillStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Bill represents the header of the transaction (Date, Total, Customer Name). Just like the Product entity, we use Hibernate filters here.
 */
@Entity
@Table(name = "bills", indexes = {
        @Index(name = "idx_bills_tenant_created_at", columnList = "tenant_id, createdAt"),
        @Index(name = "idx_bills_created_at", columnList = "createdAt"),
        @Index(name = "idx_bills_tenant_salesman", columnList = "tenant_id, salesman_employee_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_bills_tenant_request_key", columnNames = {"tenant_id", "creation_request_key"}),
        @UniqueConstraint(name = "uk_bills_tenant_tax_number", columnNames = {"tenant_id", "tax_document_number"})
})
@Getter
@Setter
@ToString(exclude = {"items", "payments"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    private Long version;

    @Column(name = "creation_request_key", length = 36, updatable = false)
    private String creationRequestKey;

    @Column(name = "creation_fingerprint", length = 64, updatable = false)
    private String creationFingerprint;

    private String customerName;
    private String contactInfo;
    @Column(length = 16) private String taxDocumentNumber;
    @Column(length = 15) private String customerGstin;
    @Column(length = 1000) private String customerAddress;
    @Column(length = 1000) private String deliveryAddress;
    @Column(length = 2) private String placeOfSupply;
    @Column(length = 20) private String taxRegistrationMode;
    @Column(length = 12) private String taxPriceMode;

    /** Cashier account display name captured when this invoice is created. */
    @Column(name = "cashier_name", length = 160)
    private String cashierName;
    private String cashierUserId;
    private Long shiftId;

    @ManyToOne
    @JoinColumn(name = "salesman_employee_id", referencedColumnName = "employee_id")
    private Salesman salesMan;

    private Double totalAmount;

    /**
     * Amount before GST (after discounts).
     */
    private Double subTotalAmount;

    /**
     * Whether GST was applied while calculating this bill.
     */
    private Boolean gstApplied;

    /**
     * GST rate used for this bill as a fraction (example: 0.18).
     */
    private Double gstRate;

    /**
     * GST amount added to the subtotal.
     */
    private Double gstAmount;

    /**
     * Instant discount given by owner at payment time (absolute amount).
     * This reduces {@link #totalAmount} and is not a due.
     */
    private Double instantDiscountAmount;
    private LocalDateTime createdAt;

    /**
     * Sum of non-credit payments (cash/upi/card etc.)
     */
    private Double paidAmount;

    /**
     * Amount still due (typically represented via CREDIT in payments).
     */
    private Double dueAmount;

    @Enumerated(EnumType.STRING)
    private BillStatus status = BillStatus.ACTIVE;

    private String cancelReason;
    private LocalDateTime cancelledAt;
    private String returnReason;
    private LocalDateTime lastReturnedAt;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    // One bill can have many items (e.g., 2 apples, 1 milk)
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private List<BillItem> items;

    // One bill can have multiple payments (e.g., part cash, part credit)
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private Set<BillPayment> payments = new LinkedHashSet<>();


    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = BillStatus.ACTIVE;
        }
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
    }
}
