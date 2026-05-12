package com.pahal.billingApp.dto;

import lombok.Data;

@Data
public class CreateBillItemRequest {
    private String productName;
    private Double quantity;
    private Double discount;

    /**
     * Editable selling price per unit at billing time.
     */
    private Double unitSellingPrice;
}

