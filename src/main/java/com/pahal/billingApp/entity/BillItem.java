package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * This is a "child" entity. It records which product was sold, at what price,
 * and in what quantity at the time of the sale.
 *
 * Tip: We store the price here separately because if you change the product
 * price tomorrow, the old bills should still show the price the customer
 * actually paid.
 */
@Entity
@Table(name = "bill_items")
@Getter
@Setter
@ToString
// We don't include the Bill reference in equals/hashCode to avoid circular
// references and potential performance issues.
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BillItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    private String productName;
    private Double quantity;

    /**
     * Editable selling price per unit at billing time (can differ from product
     * master price).
     */
    private Double unitSellingPrice;

    private Double priceAtSale;
    private Double discount;

    // We don't necessarily need a tenant_id here because
    // it is "owned" by the Bill, which already has a tenant_id.
}
