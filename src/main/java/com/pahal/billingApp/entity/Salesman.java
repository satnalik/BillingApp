package com.pahal.billingApp.entity;

import com.pahal.billingApp.context.TenantContext;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "salesmen")
@Data
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Salesman {

    @Id
    @Column(name = "employee_id")
    private String employeeId;

    private String name;
    private String phoneNumber;
    private boolean active = true;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    public void onPrePersist() {
        this.tenantId = TenantContext.getCurrentTenant();

        if (this.employeeId == null) {
            this.employeeId = String.valueOf((int) (Math.random() * 90000) + 10000);
            this.active = true;
        }
    }
}
