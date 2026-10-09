package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "gst_periods", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "period_month"}))
@Getter @Setter
public class GstPeriod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @Column(name = "period_month", nullable = false, length = 7) private String periodMonth;
    private boolean locked;
    @Column(length = 1000) private String notes;
    private LocalDateTime changedAt;
    @Column(length = 160) private String changedBy;
}
