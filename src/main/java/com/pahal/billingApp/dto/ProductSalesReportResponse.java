package com.pahal.billingApp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductSalesReportResponse {
    private java.time.LocalDateTime generatedAt;
    private String periodStart;
    private String periodEnd;
    private double totalRevenue;
    private double taxableRevenue;
    private double gstAmount;
    private double grossRevenue;
    private double discountAmount;
    private double quantitySold;
    private long bills;
    private long products;
    private ProductSalesItem topProductByQuantity;
    private ProductSalesItem topProductByRevenue;
    private List<ProductSalesItem> items = new ArrayList<>();

    @Data
    public static class ProductSalesItem {
        private Long productId;
        private String productName;
        private String barcode;
        private String category;
        private String hsnCode;
        private Double currentStock;
        private double quantitySold;
        private long billsCount;
        private double averageSellingPrice;
        private double grossRevenue;
        private double discountAmount;
        private double taxableRevenue;
        private double gstAmount;
        private double netRevenue;
    }
}
