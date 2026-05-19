package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddPurchasePaymentRequest {
    private PaymentMethod method;
    private Double amount;
    private String reference;
}
