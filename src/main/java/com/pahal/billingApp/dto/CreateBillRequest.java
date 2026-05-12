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

    /**
     * Instant discount given by owner at billing time (absolute amount).
     * Example: total 203, customer pays 200, instantDiscountAmount = 3.
     */
    private Double instantDiscountAmount;


    private List<CreateBillItemRequest> items;
    private List<CreateBillPaymentRequest> payments;
}
