package com.pahal.billingApp.dto;

import com.pahal.billingApp.enums.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class BillRegisterSummaryResponse {
    private long totalBills;
    private Double totalSales;
    private Double totalPaid;
    private Double totalDue;
    private Map<PaymentMethod, Double> paymentSplit = new LinkedHashMap<>();
}
