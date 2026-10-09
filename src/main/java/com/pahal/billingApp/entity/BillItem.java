package com.pahal.billingApp.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
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

    private Long productId;
    private String barcode;
    private String productName;
    private Double quantity;
    private Double returnedQuantity = 0.0;

    /**
     * Editable selling price per unit at billing time (can differ from product
     * master price).
     */
    private Double unitSellingPrice;

    /** Base stock-unit cost captured at sale. Null means unknown, including older invoices. */
    @JsonIgnore
    @Column(name = "unit_cost_at_sale", precision = 19, scale = 6, updatable = false)
    private BigDecimal unitCostAtSale;

    private Double priceAtSale;
    private Double discount;
    private String hsnCode;
    private Double gstRate;
    private Double taxableAmount;
    private Double gstAmount;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    private Double finalDiscountAmount;
    @Column(length = 20) private String taxCategory;
    @Column(length = 8) private String unitCode;

    public Double getNetQuantity() {
        double sold = quantity != null ? quantity : 0.0;
        double returned = returnedQuantity != null ? returnedQuantity : 0.0;
        return Math.round((sold - returned) * 100.0) / 100.0;
    }

    // We don't necessarily need a tenant_id here because
    // it is "owned" by the Bill, which already has a tenant_id.
}
