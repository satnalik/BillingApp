package com.pahal.billingApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class DailyReportResponse {
    private String date;
    private double totalSales;
    private long bills;
    private long items;
    private ProductSummary topProduct;
    private SalesmanSummary topSalesman;
    private List<ProductSummary> productBreakdown = new ArrayList<>();
    private List<SalesmanSummary> salesmanBreakdown = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private String name;
        private long qty;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesmanSummary {
        private String name;
        private String employeeId;
        private double revenue;
    }
}

