package com.pahal.billingApp.dto;

import com.pahal.billingApp.entity.GstDocument;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class GstDTO {
    private GstDTO() {}
    public record Review(String status, String notes, String supplierNoteNumber, LocalDate supplierNoteDate) {}
    public record PeriodChange(String month, boolean locked, String notes) {}
    public record ProductTax(String hsnCode, String unitCode, String taxCategory, Double gstRate) {}
    public record Adjustment(Long originalDocumentId, String requestKey, String documentType,
            LocalDate documentDate, String documentNumber, String reason, List<AdjustmentLine> lines) {}
    public record AdjustmentLine(Long productId, String productName, String hsnCode, String unitCode,
            String taxCategory, Double quantity, Double taxableAmount, Double gstRate) {}
    public record Quote(boolean configured, String registrationMode, String priceMode,
            BigDecimal subTotalAmount, BigDecimal gstAmount, BigDecimal totalAmount,
            BigDecimal beforeDiscountTotal, BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount) {}
    public record ExceptionRow(String key, String kind, Long sourceId, String reference, LocalDate date, String message) {}
    public interface LegacySale { Long getId(); LocalDateTime getCreatedAt(); }
    public interface LegacyPurchase { Long getId(); LocalDate getBillDate(); String getBillNumber(); }
    public record Hsn(String key, String hsnCode, String unitCode, String taxCategory, String customerType,
            BigDecimal gstRate, BigDecimal quantity, BigDecimal taxableAmount, BigDecimal cgstAmount,
            BigDecimal sgstAmount, BigDecimal igstAmount) {}
    public record Summary(BigDecimal salesTaxable, BigDecimal salesCgst, BigDecimal salesSgst, BigDecimal salesIgst,
            BigDecimal purchaseTaxable, BigDecimal purchaseCgst, BigDecimal purchaseSgst, BigDecimal purchaseIgst,
            BigDecimal reviewedInputCgst, BigDecimal reviewedInputSgst, BigDecimal reviewedInputIgst,
            int pendingInputDocuments, int exceptions) {}
    public record Report(LocalDateTime generatedAt, LocalDate from, LocalDate to, Summary summary,
            List<GstDocument> documents, List<Hsn> hsn, List<ExceptionRow> exceptions) {}
}
