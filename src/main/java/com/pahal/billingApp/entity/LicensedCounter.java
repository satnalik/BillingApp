package com.pahal.billingApp.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name = "licensed_counters", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"})) @Getter @Setter
public class LicensedCounter {
    @Id @Column(length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private boolean active = true;
}
