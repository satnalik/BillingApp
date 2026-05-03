package com.pahal.billingApp.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateBillRequest {
    private String customerName;
    private String contactInfo;

    /**
     * Required: salesman employeeId.
     */
    private String salesmanEmployeeId;

    private List<CreateBillItemRequest> items;
    private List<CreateBillPaymentRequest> payments;
}

