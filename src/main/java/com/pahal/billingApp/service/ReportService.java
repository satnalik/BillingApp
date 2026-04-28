package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.DailyReportResponse;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.repository.BillRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final BillRepository billRepository;

    public ReportService(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    public DailyReportResponse buildDailyReport(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        List<Bill> bills = billRepository.findByCreatedAtBetween(start, end);

        DailyReportResponse response = new DailyReportResponse();
        response.setDate(day.toString());

        double totalSales = 0.0;
        long items = 0L;

        Map<String, Long> productQty = new HashMap<>();
        Map<String, SalesmanAgg> salesmanAgg = new HashMap<>();

        for (Bill bill : bills) {
            if (bill.getTotalAmount() != null) {
                totalSales += bill.getTotalAmount();
            }

            List<BillItem> billItems = bill.getItems();
            if (billItems != null) {
                for (BillItem item : billItems) {
                    long qty = item != null && item.getQuantity() != null ? item.getQuantity() : 0;
                    items += qty;
                    String name = item != null ? item.getProductName() : null;
                    if (name != null && !name.isBlank()) {
                        productQty.merge(name, qty, Long::sum);
                    }
                }
            }

            Salesman salesman = bill.getSalesMan();
            if (salesman != null && salesman.getEmployeeId() != null) {
                String key = salesman.getEmployeeId();
                SalesmanAgg agg = salesmanAgg.computeIfAbsent(key, k -> new SalesmanAgg());
                agg.employeeId = salesman.getEmployeeId();
                agg.name = salesman.getName();
                agg.revenue += bill.getTotalAmount() != null ? bill.getTotalAmount() : 0.0;
            }
        }

        response.setTotalSales(totalSales);
        response.setBills(bills.size());
        response.setItems(items);

        List<DailyReportResponse.ProductSummary> productBreakdown = new ArrayList<>();
        for (Map.Entry<String, Long> entry : productQty.entrySet()) {
            productBreakdown.add(new DailyReportResponse.ProductSummary(entry.getKey(), entry.getValue()));
        }
        productBreakdown.sort(Comparator.comparingLong(DailyReportResponse.ProductSummary::getQty).reversed());
        response.setProductBreakdown(productBreakdown);
        response.setTopProduct(productBreakdown.isEmpty() ? null : productBreakdown.get(0));

        List<DailyReportResponse.SalesmanSummary> salesmanBreakdown = new ArrayList<>();
        for (SalesmanAgg agg : salesmanAgg.values()) {
            salesmanBreakdown.add(new DailyReportResponse.SalesmanSummary(
                    agg.name != null ? agg.name : "Unknown",
                    agg.employeeId,
                    agg.revenue
            ));
        }
        salesmanBreakdown.sort(Comparator.comparingDouble(DailyReportResponse.SalesmanSummary::getRevenue).reversed());
        response.setSalesmanBreakdown(salesmanBreakdown);
        response.setTopSalesman(salesmanBreakdown.isEmpty() ? null : salesmanBreakdown.get(0));

        return response;
    }

    private static class SalesmanAgg {
        String employeeId;
        String name;
        double revenue;
    }
}

