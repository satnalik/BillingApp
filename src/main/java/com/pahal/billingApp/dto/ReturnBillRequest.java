package com.pahal.billingApp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReturnBillRequest {
    private String reason;
    private List<ReturnBillItemRequest> items;
}
