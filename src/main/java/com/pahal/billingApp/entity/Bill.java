package com.pahal.billingApp.entity;

import com.pahal.billingApp.context.TenantContext;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Bill represents the header of the transaction (Date, Total, Customer Name). Just like the Product entity, we use Hibernate filters here.
 */
@Entity
@Table(name = "bills")
@Data
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;
    private String contactInfo;

    @ManyToOne
    @JoinColumn(name = "salesman_employee_id", referencedColumnName = "employee_id")
    private Salesman salesMan;

    private Double totalAmount;
    private LocalDateTime createdAt;


    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    // One bill can have many items (e.g., 2 apples, 1 milk)
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private List<BillItem> items;



    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            this.tenantId = currentTenant;
        }
    }
}