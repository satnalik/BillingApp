package com.pahal.billingApp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SalesProfitReportDTO {
    private SalesProfitReportDTO() {}

    /** Internal projection, never returned as an invoice response. */
    public interface SaleRow {
        Long getBillId();
        LocalDateTime getCreatedAt();
        Double getBillDiscount();
        Double getFinalDiscountAmount();
        Long getItemId();
        Long getProductId();
        String getProductName();
        String getBarcode();
        String getCategory();
        Double getQuantity();
        Double getReturnedQuantity();
        Double getTaxableAmount();
        Double getGstAmount();
        Double getCgstAmount();
        Double getSgstAmount();
        Double getIgstAmount();
        Double getUnitSellingPrice();
        Double getDiscount();
        BigDecimal getUnitCostAtSale();
    }

    public record Statistics(BigDecimal netSales, BigDecimal coveredSales,
            BigDecimal costOfGoodsSold, BigDecimal estimatedGrossProfit, BigDecimal marginPercent,
            BigDecimal billDiscount, BigDecimal gstAmount, BigDecimal quantitySold,
            BigDecimal costedQuantity, BigDecimal missingCostQuantity, int saleLines,
            int costedLines, int missingCostLines, int lossMakingLines, int billsCount,
            BigDecimal costCoveragePercent, String coverageStatus) {}

    public record ProductProfit(String key, Long productId, String productName, String barcode,
            String category, Statistics statistics, BigDecimal completeGrossProfit,
            BigDecimal completeMarginPercent) {}

    public record DailyProfit(LocalDate date, Statistics statistics) {}

    public record Report(LocalDateTime generatedAt, LocalDate from, LocalDate to, String costingBasis,
            Statistics summary, List<ProductProfit> items, List<DailyProfit> daily) {}
}
