package com.pahal.billingApp.entity;

import com.pahal.billingApp.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "bill_payments")
@Getter
@Setter
@ToString(exclude = "bill")
public class BillPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // We don't use @ManyToOne here because we want to avoid the circular reference
    // when serializing to JSON. Instead, we will set the bill_id directly in the
    // database.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // The bill_id column will be set manually in the service layer when we create a
    // payment for a bill.
    @JoinColumn(name = "bill_id", nullable = false)
    @JsonIgnore
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(nullable = false)
    private Double amount;

    /**
     * Optional reference (UPI txn id, card auth code, etc.)
     */
    private String reference;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onPrePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
