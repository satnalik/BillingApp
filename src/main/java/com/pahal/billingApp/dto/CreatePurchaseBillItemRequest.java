package com.pahal.billingApp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePurchaseBillItemRequest {
    private Long productId;
    private Double quantity;
    private Double purchasePrice;
    private Double sellingPrice;
}
