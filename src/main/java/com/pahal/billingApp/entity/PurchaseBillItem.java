package com.pahal.billingApp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "purchase_bill_items")
@Getter
@Setter
@ToString(exclude = { "purchaseBill", "product" })
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PurchaseBillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_bill_id", nullable = false)
    @JsonIgnore
    private PurchaseBill purchaseBill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String barcode;
    private String productName;
    private Double quantity;
    private Double returnedQuantity = 0.0;
    private Double purchasePrice;
    private Double sellingPrice;
    private Double lineTotal;
    private Double taxableAmount;
    private Double gstAmount;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    private Double gstRate;
    @Column(length = 8) private String hsnCode;
    @Column(length = 8) private String unitCode;
    @Column(length = 20) private String taxCategory;
}
