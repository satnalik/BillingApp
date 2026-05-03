package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 Many items belong to one order
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    // 🛍️ Product reference (can later become FK)
    private String productName;

    private String sku;

    private Integer quantity;

    private Double price;

    private Double totalPrice;
}