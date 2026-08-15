package com.pahal.billingApp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePurchaseBillItemRequest {
    private Long productId;
    private String barcode;
    private Double quantityPerScan;
    private Double quantity;
    private Double purchasePrice;
    private Double sellingPrice;
}
