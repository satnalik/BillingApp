package com.pahal.billingApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SalesReportResponse {
    /**
     * Backwards-compatible label field (some UIs show this as the selected day/month).
     * Examples: "2026-04-28", "2026-04", "2026-04-01..2026-04-28"
     */
    private String date;

    /**
     * Optional explicit range (inclusive start, exclusive end).
     */
    private String periodStart;
    private String periodEnd;

    private double totalSales;
    private long bills;
    private long items;
    private DailyReportResponse.ProductSummary topProduct;
    private DailyReportResponse.SalesmanSummary topSalesman;
    private List<DailyReportResponse.ProductSummary> productBreakdown = new ArrayList<>();
    private List<DailyReportResponse.SalesmanSummary> salesmanBreakdown = new ArrayList<>();
}

