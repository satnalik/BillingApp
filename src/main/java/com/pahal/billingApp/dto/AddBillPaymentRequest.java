package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Data;

@Data
public class AddBillPaymentRequest {
    private PaymentMethod method;
    private Double amount;
    private String reference;
}

