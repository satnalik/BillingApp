package com.pahal.billingApp.dto;

import lombok.Data;

@Data
public class ReturnBillItemRequest {
    private Long billItemId;
    private Long productId;
    private String barcode;
    private Double quantity;
}
