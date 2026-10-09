package com.pahal.billingApp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pahal.billingApp.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_payments", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_purchase_payment_request", columnNames = {"purchase_bill_id", "request_key"}))
@Getter
@Setter
@ToString(exclude = "purchaseBill")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PurchasePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_bill_id", nullable = false)
    @JsonIgnore
    private PurchaseBill purchaseBill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(nullable = false)
    private Double amount;

    private String reference;
    private Boolean refund = false;
    @Column(length = 36)
    @JsonIgnore
    private String requestKey;
    @Column(length = 64)
    @JsonIgnore
    private String fingerprint;
    @Column(length = 160)
    private String actorName;
    private LocalDateTime createdAt;

    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
