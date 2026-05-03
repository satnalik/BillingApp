package com.pahal.billingApp.entity;

import com.pahal.billingApp.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔢 Order number (useful for UI & invoices)
    @Column(unique = true, nullable = false)
    private String orderNumber;

    // 💰 Total bill amount
    @Column(nullable = false)
    private Double totalAmount;

    // 🧾 Order status (COMPLETED, CANCELLED, etc.)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // 🕒 REQUIRED for reports
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 👤 (Optional for now, useful later)
    private String customerName;

    // 🏬 (IMPORTANT for future multi-store support)
    private String storeId;

    // 🔗 Order Items (One order → many items)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;


    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
