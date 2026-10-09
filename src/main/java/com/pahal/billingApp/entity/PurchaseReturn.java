package com.pahal.billingApp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Immutable return document; stock and financial credit are committed together. */
@Entity
@Table(name = "purchase_returns", uniqueConstraints = @UniqueConstraint(
        name = "uk_purchase_return_request", columnNames = {"purchase_bill_id", "request_key"}))
@Getter @Setter
public class PurchaseReturn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_bill_id", nullable = false)
    @JsonIgnore
    private PurchaseBill purchaseBill;
    @Column(name = "request_key", nullable = false, length = 36)
    @JsonIgnore
    private String requestKey;
    @Column(nullable = false, length = 64) @JsonIgnore
    private String fingerprint;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private Double creditAmount;
    @Column(nullable = false, length = 160)
    private String actorName;
    @Column(length = 160)
    private String actorUserId;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @ElementCollection
    @CollectionTable(name = "purchase_return_items", joinColumns = @JoinColumn(name = "purchase_return_id"))
    @OrderColumn(name = "line_index")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<Item> items = new ArrayList<>();

    @Embeddable @Getter @Setter
    public static class Item {
        @Column(nullable = false)
        private Long purchaseItemId;
        @Column(nullable = false)
        private Long productId;
        private String productName;
        @Column(nullable = false)
        private Double quantity;
        @Column(nullable = false)
        private Double creditAmount;
    }
}
