package com.pahal.billingApp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read models built from existing invoices and products; no separate report ledger. */
public final class OperationalReportDTO {
    private OperationalReportDTO() {}

    public interface OutstandingBillRow {
        Long getId();
        String getCustomerName();
        String getContactInfo();
        LocalDateTime getCreatedAt();
        Double getTotalAmount();
        Double getPaidAmount();
        Double getDueAmount();
    }

    public interface StockRow {
        Long getProductId();
        String getProductName();
        String getBarcode();
        String getCategory();
        String getSupplierName();
        Double getStockQuantity();
        Double getCostPrice();
    }

    public record OutstandingInvoice(Long billId, String billNumber, String customerName,
            LocalDateTime createdAt, Long ageDays, String ageBucket,
            BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal dueAmount) {}

    public record CustomerOutstanding(String key, String customerName, String contactInfo,
            LocalDate oldestInvoiceDate, BigDecimal outstandingAmount, int unpaidInvoices,
            BigDecimal age0To30, BigDecimal age31To60, BigDecimal age61To90,
            BigDecimal ageOver90, BigDecimal unknownAge, List<OutstandingInvoice> invoices) {}

    public record OutstandingReport(LocalDateTime generatedAt, LocalDate ageDate,
            List<CustomerOutstanding> items) {}

    public record StockItem(Long productId, String productName, String barcode, String category,
            String supplierName, Double stockQuantity, BigDecimal costPrice,
            BigDecimal estimatedValue, String stockStatus) {}

    public record StockReport(LocalDateTime generatedAt, List<StockItem> items) {}

    public record MovementReport(LocalDateTime generatedAt,
            List<com.pahal.billingApp.entity.StockMovement> items) {}
}
