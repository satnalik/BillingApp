package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.enums.PurchaseStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class PurchaseBillResponse {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private String billNumber;
    private LocalDate billDate;
    private Double subTotalAmount;
    private Double discountAmount;
    private Double taxAmount;
    private Double totalAmount;
    private Double paidAmount;
    private Double dueAmount;
    private PurchaseStatus status;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private String notes;
    private LocalDateTime createdAt;
    private List<Item> items;
    private List<Payment> payments;

    @Getter
    @Setter
    public static class Item {
        private Long productId;
        private String barcode;
        private String productName;
        private Double quantity;
        private Double purchasePrice;
        private Double sellingPrice;
        private Double lineTotal;
    }

    @Getter
    @Setter
    public static class Payment {
        private PaymentMethod method;
        private Double amount;
        private String reference;
        private LocalDateTime createdAt;
    }
}
