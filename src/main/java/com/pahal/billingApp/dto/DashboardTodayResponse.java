package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DashboardTodayResponse {
    private String date;
    private Double todaySales;
    private Long billCount;
    private Map<PaymentMethod, Double> paymentSplit = new LinkedHashMap<>();
    private Double customerDue;
    private Double supplierDue;
    private Long lowStockCount;
    private Double lowStockThreshold;
    private List<LowStockProduct> lowStockItems;

    @Getter
    @Setter
    public static class LowStockProduct {
        private Long id;
        private String name;
        private String barcode;
        private Double stockQuantity;
        private String category;
        private String supplierName;
    }
}
