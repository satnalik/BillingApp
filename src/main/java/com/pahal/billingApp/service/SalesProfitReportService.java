package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.SalesProfitReportDTO.*;
import com.pahal.billingApp.repository.BillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SalesProfitReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final BillRepository bills;

    public SalesProfitReportService(BillRepository bills) { this.bills = bills; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @com.pahal.billingApp.licensing.RequiresFeature(com.pahal.billingApp.licensing.Feature.PROFIT_REPORTS)
    public Report report(LocalDate from, LocalDate to, String productName, String barcode,
            String category, String view) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Choose a valid date range (From must be on or before To).");
        }
        String selection = clean(view).toUpperCase(Locale.ROOT);
        if (selection.isEmpty()) selection = "ALL";
        if (!Set.of("ALL", "COMPLETE", "PARTIAL", "MISSING", "LOSS").contains(selection)) {
            throw new IllegalArgumentException("Invalid profit report view.");
        }
        Map<Long, List<SaleRow>> invoices = new LinkedHashMap<>();
        for (SaleRow row : bills.findProfitRows(StockService.requireTenant(), from.atStartOfDay(),
                to.plusDays(1).atStartOfDay())) {
            invoices.computeIfAbsent(row.getBillId(), ignored -> new ArrayList<>()).add(row);
        }
        List<Line> matching = new ArrayList<>();
        Map<String, Accumulator> products = new LinkedHashMap<>();
        for (List<SaleRow> invoice : invoices.values()) {
            List<Line> lines = invoice.stream().map(Line::new).filter(line -> line.quantity.signum() > 0).toList();
            if (invoice.get(0).getFinalDiscountAmount() == null)
                allocateDiscount(lines, money(decimal(invoice.get(0).getBillDiscount()).max(BigDecimal.ZERO)));
            for (Line line : lines) {
                SaleRow row = line.row;
                if (!contains(row.getProductName(), productName) || !contains(row.getBarcode(), barcode)
                        || (!clean(category).isEmpty() && !clean(row.getCategory()).equalsIgnoreCase(clean(category)))) continue;
                matching.add(line);
                products.computeIfAbsent(line.key, ignored -> new Accumulator()).add(line);
            }
        }
        // Evaluate coverage/loss filters on the entire product, then rebuild totals and dates for that selection.
        List<ProductProfit> items = new ArrayList<>();
        Set<String> selected = new HashSet<>();
        for (Map.Entry<String, Accumulator> entry : products.entrySet()) {
            Statistics stats = entry.getValue().statistics();
            boolean include = switch (selection) {
                case "COMPLETE", "PARTIAL", "MISSING" -> selection.equals(stats.coverageStatus());
                case "LOSS" -> stats.lossMakingLines() > 0;
                default -> true;
            };
            if (!include) continue;
            selected.add(entry.getKey());
            SaleRow row = entry.getValue().latest;
            boolean complete = "COMPLETE".equals(stats.coverageStatus());
            items.add(new ProductProfit(entry.getKey(), row.getProductId(), row.getProductName(),
                    row.getBarcode(), row.getCategory(), stats,
                    complete ? stats.estimatedGrossProfit() : null, complete ? stats.marginPercent() : null));
        }
        items.sort(Comparator.comparing(ProductProfit::completeGrossProfit,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ProductProfit::key));
        Accumulator total = new Accumulator();
        Map<LocalDate, Accumulator> days = new TreeMap<>();
        for (Line line : matching) {
            if (!selected.contains(line.key)) continue;
            total.add(line);
            days.computeIfAbsent(line.row.getCreatedAt().toLocalDate(), ignored -> new Accumulator()).add(line);
        }
        List<DailyProfit> daily = days.entrySet().stream()
                .map(entry -> new DailyProfit(entry.getKey(), entry.getValue().statistics())).toList();
        return new Report(LocalDateTime.now(), from, to, "PRODUCT_COST_AT_SALE",
                total.statistics(), items, daily);
    }

    /** Largest remainder allocation in paise. Stable item-ID order breaks ties.
     * All remaining lines participate, including unknown-cost and filtered-out products. */
    private static void allocateDiscount(List<Line> lines, BigDecimal discount) {
        if (lines.isEmpty() || discount.signum() == 0) return;
        List<BigDecimal> weights = lines.stream().map(line -> line.taxable.max(BigDecimal.ZERO)).toList();
        BigDecimal weightTotal = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightTotal.signum() == 0) {
            weights = lines.stream().map(line -> line.taxable.add(line.gst).max(BigDecimal.ZERO)).toList();
            weightTotal = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (weightTotal.signum() == 0) {
            weights = lines.stream().map(line -> line.quantity).toList();
            weightTotal = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal cents = discount.movePointRight(2), assigned = BigDecimal.ZERO;
        List<BigDecimal> remainders = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal numerator = cents.multiply(weights.get(index));
            BigDecimal floor = numerator.divide(weightTotal, 0, RoundingMode.DOWN);
            lines.get(index).allocatedDiscount = floor.movePointLeft(2);
            assigned = assigned.add(floor);
            remainders.add(numerator.subtract(floor.multiply(weightTotal)));
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) order.add(index);
        order.sort(Comparator.<Integer, BigDecimal>comparing(remainders::get).reversed().thenComparingInt(index -> index));
        int leftover = cents.subtract(assigned).intValueExact();
        for (int index = 0; index < leftover; index++) {
            Line line = lines.get(order.get(index));
            line.allocatedDiscount = line.allocatedDiscount.add(new BigDecimal("0.01"));
        }
    }

    private static final class Line {
        final SaleRow row;
        final String key;
        final BigDecimal quantity, taxable, gst, cost;
        BigDecimal allocatedDiscount = ZERO;

        Line(SaleRow row) {
            this.row = row;
            key = row.getProductId() == null ? "name:" + clean(row.getProductName()) : "product:" + row.getProductId();
            BigDecimal original = decimal(row.getQuantity());
            quantity = money(original.subtract(decimal(row.getReturnedQuantity())).max(BigDecimal.ZERO));
            BigDecimal ratio = original.signum() > 0 ? quantity.divide(original, 16, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal originalTaxable = row.getTaxableAmount() == null
                    ? decimal(row.getUnitSellingPrice()).multiply(original)
                            .multiply(BigDecimal.ONE.subtract(decimal(row.getDiscount()).movePointLeft(2)))
                    : decimal(row.getTaxableAmount());
            if (row.getFinalDiscountAmount() != null && original.signum() > 0) {
                BigDecimal returned = original.subtract(quantity);
                taxable = remaining(originalTaxable, original, returned);
                gst = remaining(decimal(row.getCgstAmount()), original, returned)
                        .add(remaining(decimal(row.getSgstAmount()), original, returned))
                        .add(remaining(decimal(row.getIgstAmount()), original, returned));
                allocatedDiscount = remaining(decimal(row.getFinalDiscountAmount()), original, returned);
            } else {
                taxable = money(originalTaxable.multiply(ratio));
                gst = money(decimal(row.getGstAmount()).multiply(ratio));
            }
            BigDecimal snapshot = row.getUnitCostAtSale();
            cost = snapshot == null || snapshot.signum() < 0 ? null : money(snapshot.multiply(quantity));
        }

        BigDecimal sales() { return row.getFinalDiscountAmount() == null ? taxable.subtract(allocatedDiscount) : taxable; }
    }

    private static final class Accumulator {
        BigDecimal sales = ZERO, covered = ZERO, cost = ZERO, discount = ZERO, gst = ZERO;
        BigDecimal quantity = ZERO, costedQuantity = ZERO, missingQuantity = ZERO;
        int lines, costed, losses;
        final Set<Long> billIds = new HashSet<>();
        SaleRow latest;

        void add(Line line) {
            latest = line.row;
            lines++;
            billIds.add(line.row.getBillId());
            sales = sales.add(line.sales());
            quantity = quantity.add(line.quantity);
            discount = discount.add(line.allocatedDiscount);
            gst = gst.add(line.gst);
            if (line.cost == null) {
                missingQuantity = missingQuantity.add(line.quantity);
            } else {
                costed++;
                costedQuantity = costedQuantity.add(line.quantity);
                covered = covered.add(line.sales());
                cost = cost.add(line.cost);
                if (line.sales().compareTo(line.cost) < 0) losses++;
            }
        }

        Statistics statistics() {
            BigDecimal profit = costed == 0 ? null : covered.subtract(cost);
            BigDecimal margin = profit == null || covered.signum() <= 0 ? null
                    : profit.multiply(BigDecimal.valueOf(100)).divide(covered, 2, RoundingMode.HALF_UP);
            BigDecimal coverage = lines == 0 ? null : BigDecimal.valueOf(costed * 100L)
                    .divide(BigDecimal.valueOf(lines), 2, RoundingMode.HALF_UP);
            String status = lines == 0 ? "EMPTY" : costed == 0 ? "MISSING" : costed == lines ? "COMPLETE" : "PARTIAL";
            return new Statistics(sales, covered, cost, profit, margin, discount, gst, quantity,
                    costedQuantity, missingQuantity, lines, costed, lines - costed, losses,
                    billIds.size(), coverage, status);
        }
    }

    private static boolean contains(String value, String filter) {
        return clean(value).toLowerCase(Locale.ROOT).contains(clean(filter).toLowerCase(Locale.ROOT));
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static BigDecimal decimal(Double value) {
        return value == null || !Double.isFinite(value) ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal remaining(BigDecimal amount, BigDecimal original, BigDecimal returned) {
        return money(amount).subtract(amount.multiply(returned).divide(original, 2, RoundingMode.HALF_UP));
    }
}
