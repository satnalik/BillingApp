package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.enums.BillStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BillResponse {
    private Long id;
    /**
     * Professional, user-facing invoice number (derived from id).
     * Example: INV-00000016
     */
    private String billNumber;
    private String customerName;
    private String contactInfo;
    private String customerGstin;
    private String customerAddress;
    private String deliveryAddress;
    private String placeOfSupply;
    private String taxRegistrationMode;
    private String taxPriceMode;
    private String cashierName;
    private String cashierUserId;
    private Long shiftId;
    private Double totalAmount;
    private Double subTotalAmount;
    private Boolean gstApplied;
    private Double gstRate;
    private Double gstAmount;
    private Double instantDiscountAmount;
    private LocalDateTime createdAt;

    private String tenantId;

    private String salesmanEmployeeId;
    private String salesmanName;

    private Double paidAmount;
    private Double dueAmount;
    private BillStatus status;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private String returnReason;
    private LocalDateTime lastReturnedAt;

    private List<Item> items;
    private List<Payment> payments;

    @Data
    public static class Item {
        private Long productId;
        private String barcode;
        private String productName;
        private Double quantity;
        private Double returnedQuantity;
        private Double netQuantity;
        private Double unitSellingPrice;
        private Double discount;
        private String hsnCode;
        private Double gstRate;
        private Double taxableAmount;
        private Double gstAmount;
        private Double cgstAmount;
        private Double sgstAmount;
        private Double igstAmount;
        private Double finalDiscountAmount;
        private String taxCategory;
        private String unitCode;
    }

    @Data
    public static class Payment {
        private Long id;
        private PaymentMethod method;
        private Double amount;
        private String reference;
        private LocalDateTime createdAt;
    }
}
