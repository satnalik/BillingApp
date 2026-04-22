package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * This is a "child" entity. It records which product was sold, at what price, and in what quantity at the time of the sale.
 *
 * Tip: We store the price here separately because if you change the product price tomorrow, the old bills should still show the price the customer actually paid.
 */
@Entity
@Table(name = "bill_items")
@Data
public class BillItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;
    private Integer quantity;
    private Double priceAtSale;
    private Double discount;

    // We don't necessarily need a tenant_id here because
    // it is "owned" by the Bill, which already has a tenant_id.
}
