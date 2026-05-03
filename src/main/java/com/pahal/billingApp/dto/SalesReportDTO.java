package com.pahal.billingApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalesReportDTO {

    private String date;
    private Double totalSales;
    private Long totalOrders;
}
