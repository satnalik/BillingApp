package com.pahal.billingApp.entity;

import com.pahal.billingApp.context.TenantContext;
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

    private String customerName;
    private String contactInfo;

    @ManyToOne
    @JoinColumn(name = "salesman_employee_id", referencedColumnName = "employee_id")
    private Salesman salesMan;

    private Double totalAmount;
    private LocalDateTime createdAt;

    /**
     * Sum of non-credit payments (cash/upi/card etc.)
     */
    private Double paidAmount;

    /**
     * Amount still due (typically represented via CREDIT in payments).
     */
    private Double dueAmount;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    // One bill can have many items (e.g., 2 apples, 1 milk)
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private List<BillItem> items;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private Set<BillPayment> payments = new LinkedHashSet<>();


    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
    }
}
