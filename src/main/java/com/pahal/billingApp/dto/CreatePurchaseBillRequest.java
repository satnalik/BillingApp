package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreatePurchaseBillRequest {
    private Long supplierId;
    private String billNumber;
    private LocalDate billDate;
    private Double discountAmount;
    private Double taxAmount;
    private Double paidAmount;
    private PaymentMethod paymentMethod;
    private String paymentReference;
    private String notes;
    private List<CreatePurchaseBillItemRequest> items;
}
