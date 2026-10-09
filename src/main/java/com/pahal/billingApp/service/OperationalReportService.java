package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.OperationalReportDTO.*;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OperationalReportService {
    private final BillRepository bills;
    private final ProductRepository products;

    public OperationalReportService(BillRepository bills, ProductRepository products) {
        this.bills = bills;
        this.products = products;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OutstandingReport outstanding() {
        String tenant = StockService.requireTenant();
        LocalDateTime generated = LocalDateTime.now();
        LocalDate today = generated.toLocalDate();
        Map<String, List<OutstandingBillRow>> groups = new LinkedHashMap<>();
        for (OutstandingBillRow bill : bills.findOutstandingReportRows(tenant)) {
            String contact = contactKey(bill.getContactInfo());
            // Bills have contact snapshots, not a customer FK. Names alone cannot identify a customer.
            String key = contact == null ? "invoice:" + bill.getId() : "contact:" + contact;
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bill);
        }
        List<CustomerOutstanding> items = new ArrayList<>();
        groups.forEach((key, rows) -> {
            List<OutstandingInvoice> invoices = new ArrayList<>();
            BigDecimal[] buckets = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            BigDecimal total = BigDecimal.ZERO;
            LocalDate oldest = null;
            for (OutstandingBillRow bill : rows) {
                LocalDate date = bill.getCreatedAt() == null ? null : bill.getCreatedAt().toLocalDate();
                Long age = date == null ? null : Math.max(0, ChronoUnit.DAYS.between(date, today));
                int bucket = age == null ? 4 : age <= 30 ? 0 : age <= 60 ? 1 : age <= 90 ? 2 : 3;
                BigDecimal due = money(bill.getDueAmount());
                buckets[bucket] = buckets[bucket].add(due);
                total = total.add(due);
                if (date != null && (oldest == null || date.isBefore(oldest))) oldest = date;
                invoices.add(new OutstandingInvoice(bill.getId(), String.format("INV-%08d", bill.getId()),
                        bill.getCustomerName(), bill.getCreatedAt(), age,
                        new String[]{"0-30", "31-60", "61-90", "90+", "UNKNOWN"}[bucket],
                        money(bill.getTotalAmount()), money(bill.getPaidAmount()), due));
            }
            OutstandingBillRow latest = rows.get(0);
            items.add(new CustomerOutstanding(key, latest.getCustomerName(), latest.getContactInfo(), oldest,
                    total, invoices.size(), buckets[0], buckets[1], buckets[2], buckets[3], buckets[4], invoices));
        });
        items.sort(Comparator.comparing(CustomerOutstanding::outstandingAmount).reversed()
                .thenComparing(CustomerOutstanding::key));
        return new OutstandingReport(generated, today, items);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @com.pahal.billingApp.licensing.RequiresFeature(com.pahal.billingApp.licensing.Feature.ADVANCED_REPORTS)
    public StockReport stock() {
        String tenant = StockService.requireTenant();
        List<StockItem> items = new ArrayList<>();
        for (StockRow row : products.findStockReportRows(tenant)) {
            Double quantity = row.getStockQuantity() == null ? 0.0 : row.getStockQuantity();
            if (!Double.isFinite(quantity)) quantity = null;
            Double rawCost = row.getCostPrice();
            BigDecimal cost = rawCost != null && Double.isFinite(rawCost) && rawCost >= 0
                    ? BigDecimal.valueOf(rawCost) : null;
            BigDecimal value = quantity == null || quantity < 0 || cost == null ? null
                    : cost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            String status = quantity == null ? "INVALID" : quantity < 0 ? "NEGATIVE" : quantity == 0 ? "OUT" : "IN_STOCK";
            items.add(new StockItem(row.getProductId(), row.getProductName(), row.getBarcode(), row.getCategory(),
                    row.getSupplierName(), quantity, cost, value, status));
        }
        return new StockReport(LocalDateTime.now(), items);
    }

    private static String contactKey(String value) {
        if (value == null || value.isBlank()) return null;
        String contact = value.trim().toLowerCase(Locale.ROOT);
        if (Set.of("-", "n/a", "na", "none", "null", "unknown").contains(contact)) return null;
        if (contact.matches("[+0-9() .-]+")) {
            contact = contact.replaceAll("[() .-]", "");
            if (!contact.matches("\\+?[0-9]{6,15}")) return null;
        }
        return contact;
    }

    private static BigDecimal money(Double value) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException("An invoice has an invalid balance. Correct it before generating the report.");
        }
        return BigDecimal.valueOf(value == null ? 0 : value).setScale(2, RoundingMode.HALF_UP);
    }
}
