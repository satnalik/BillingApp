package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shift_cash_movements", indexes = @Index(name = "idx_shift_cash_movement", columnList = "tenant_id, shift_id, id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_shift_cash_request", columnNames = {"shift_id", "request_key"}))
@Getter @Setter
public class ShiftCashMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String tenantId;
    @Column(nullable = false) private Long shiftId;
    @Column(nullable = false, length = 36) private String requestKey;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(nullable = false) private boolean cashIn;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 500) private String reason;
    @Column(length = 240) private String reference;
    @Column(nullable = false) private String actorUserId;
    @Column(nullable = false, length = 160) private String actorName;
    @Column(nullable = false) private LocalDateTime createdAt;
}
