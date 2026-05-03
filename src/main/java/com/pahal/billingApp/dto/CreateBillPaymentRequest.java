package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Data;

@Data
public class CreateBillPaymentRequest {
    private PaymentMethod method;
    private Double amount;
    private String reference;
}

