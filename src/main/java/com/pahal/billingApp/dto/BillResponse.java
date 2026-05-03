package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
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
    private Double totalAmount;
    private LocalDateTime createdAt;

    private String tenantId;

    private String salesmanEmployeeId;
    private String salesmanName;

    private Double paidAmount;
    private Double dueAmount;

    private List<Item> items;
    private List<Payment> payments;

    @Data
    public static class Item {
        private String productName;
        private Integer quantity;
        private Double unitSellingPrice;
        private Double discount;
    }

    @Data
    public static class Payment {
        private PaymentMethod method;
        private Double amount;
        private String reference;
    }
}
