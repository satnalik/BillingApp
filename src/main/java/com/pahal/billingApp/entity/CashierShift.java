package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashier_shifts", indexes = @Index(name = "idx_shifts_tenant_opened", columnList = "tenant_id, opened_at, id"),
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_shift_active_cashier", columnNames = {"tenant_id", "cashier_user_id", "active_marker"}),
            @UniqueConstraint(name = "uk_shift_active_counter", columnNames = {"tenant_id", "license_counter_id", "active_marker"}),
            @UniqueConstraint(name = "uk_shift_open_request", columnNames = {"tenant_id", "cashier_user_id", "open_request_key"})})
@Getter @Setter
public class CashierShift {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String tenantId;
    @Column(nullable = false) private String cashierUserId;
    @Column(nullable = false, length = 160) private String cashierName;
    @Column(length = 120) private String counterName;
    @Column(length = 36) private String licenseCounterId;
    /** NULL on closed rows permits multiple past shifts, but only one active shift per cashier. */
    private Integer activeMarker = 1;
    @Column(nullable = false, length = 36) private String openRequestKey;
    @Column(nullable = false, length = 64) private String openFingerprint;
    @Column(length = 36) private String closeRequestKey;
    @Column(length = 64) private String closeFingerprint;
    @Column(nullable = false) private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private String closedByUserId;
    @Column(length = 160) private String closedByName;
    @Column(length = 1000) private String closeNotes;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal openingCash;
    @Column(precision = 19, scale = 2) private BigDecimal expectedCash;
    @Column(precision = 19, scale = 2) private BigDecimal countedCash;
    @Column(precision = 19, scale = 2) private BigDecimal variance;
    @Column(precision = 19, scale = 2) private BigDecimal cashReceived;
    @Column(precision = 19, scale = 2) private BigDecimal cashRefunded;
    @Column(precision = 19, scale = 2) private BigDecimal upiReceived;
    @Column(precision = 19, scale = 2) private BigDecimal upiRefunded;
    @Column(precision = 19, scale = 2) private BigDecimal cardReceived;
    @Column(precision = 19, scale = 2) private BigDecimal cardRefunded;
    @Column(precision = 19, scale = 2) private BigDecimal manualCashIn;
    @Column(precision = 19, scale = 2) private BigDecimal manualCashOut;
}
