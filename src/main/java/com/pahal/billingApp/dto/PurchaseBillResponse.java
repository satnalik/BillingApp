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
    private Double returnedAmount;
    private Double refundedAmount;
    private Double netAmount;
    private Double supplierCredit;
    private String returnStatus;
    private List<Return> returns;
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
        private Long id;
        private Long productId;
        private String barcode;
        private String productName;
        private Double quantity;
        private Double returnedQuantity;
        private Double remainingQuantity;
        private Double returnCreditAmount;
        private Double purchasePrice;
        private Double sellingPrice;
        private Double lineTotal;
        private String hsnCode;
        private String unitCode;
        private String taxCategory;
        private Double gstRate;
        private Double taxableAmount;
        private Double gstAmount;
        private Double cgstAmount;
        private Double sgstAmount;
        private Double igstAmount;
    }

    public record Return(Long id, String reason, Double creditAmount, String actorName,
                         LocalDateTime createdAt, List<ReturnItem> items) {}
    public record ReturnItem(Long purchaseItemId, Long productId, String productName, Double quantity, Double creditAmount) {}

    @Getter
    @Setter
    public static class Payment {
        private Boolean refund;
        private String actorName;
        private PaymentMethod method;
        private Double amount;
        private String reference;
        private LocalDateTime createdAt;
    }
}
