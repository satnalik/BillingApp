package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "license_audit") @Getter @Setter
public class LicenseAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String tenantId;
    @Column(nullable = false) private String action;
    @Column(nullable = false, length = 1000) private String details;
    private String actor;
    @Column(nullable = false) private LocalDateTime createdAt;
}
