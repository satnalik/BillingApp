package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.DayEndReportResponse;
import com.pahal.billingApp.dto.DailyReportResponse;
import com.pahal.billingApp.dto.PaymentMethodAggProjection;
import com.pahal.billingApp.dto.ProductSalesAggProjection;
import com.pahal.billingApp.dto.ProductSalesReportResponse;
import com.pahal.billingApp.dto.ReportAggProjection;
import com.pahal.billingApp.dto.SalesReportDTO;
import com.pahal.billingApp.dto.SalesReportResponse;
import com.pahal.billingApp.dto.SalesmanPaymentAggProjection;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final BillRepository billRepository;

    @Autowired
     private OrderRepository orderRepository;

    public ReportService(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    @Cacheable(cacheNames = "reports", key = "T(com.pahal.billingApp.context.TenantContext).getCurrentTenant() + ':daily:' + #day")
    public DailyReportResponse buildDailyReport(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        SalesReportResponse report = buildSalesReport(start, end);

        DailyReportResponse response = new DailyReportResponse();
        response.setDate(day.toString());
        response.setTotalSales(report.getTotalSales());
        response.setBills(report.getBills());
        response.setItems(report.getItems());
        response.setTopProduct(report.getTopProduct());
        response.setTopSalesman(report.getTopSalesman());
        response.setProductBreakdown(report.getProductBreakdown());
        response.setSalesmanBreakdown(report.getSalesmanBreakdown());
        return response;
    }

    @Cacheable(cacheNames = "reports", key = "T(com.pahal.billingApp.context.TenantContext).getCurrentTenant() + ':monthly:' + #month")
    public SalesReportResponse buildMonthlyReport(YearMonth month) {
        LocalDate startDay = month.atDay(1);
        LocalDateTime start = startDay.atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        SalesReportResponse report = buildSalesReport(start, end);
        report.setDate(month.toString()); // YYYY-MM
        report.setPeriodStart(start.toString());
        report.setPeriodEnd(end.toString());
        return report;
    }

    /**
     * Range is [from, to] inclusive in API, converted to [fromStart, to+1dayStart) for querying.
     */
    @Cacheable(cacheNames = "reports", key = "T(com.pahal.billingApp.context.TenantContext).getCurrentTenant() + ':range:' + #from + ':' + #to")
    public SalesReportResponse buildRangeReport(LocalDate from, LocalDate to) {
        LocalDate startDay = from;
        LocalDate endDayExclusive = to.plusDays(1);
        LocalDateTime start = startDay.atStartOfDay();
        LocalDateTime end = endDayExclusive.atStartOfDay();

        SalesReportResponse report = buildSalesReport(start, end);
        report.setDate(from + ".." + to);
        report.setPeriodStart(start.toString());
        report.setPeriodEnd(end.toString());
        return report;
    }

    @Cacheable(cacheNames = "reports", key = "T(com.pahal.billingApp.context.TenantContext).getCurrentTenant() + ':productSales:' + #from + ':' + #to + ':' + #productName + ':' + #barcode + ':' + #category + ':' + #salesmanId")
    public ProductSalesReportResponse buildProductSalesReport(
            LocalDate from,
            LocalDate to,
            String productName,
            String barcode,
            String category,
            String salesmanId) {
        LocalDate startDay = from != null ? from : LocalDate.now();
        LocalDate endDay = to != null ? to : startDay;
        LocalDateTime start = startDay.atStartOfDay();
        LocalDateTime end = endDay.plusDays(1).atStartOfDay();

        List<ProductSalesReportResponse.ProductSalesItem> items = new ArrayList<>();
        String tenantId = TenantContext.getCurrentTenant();
        String normalizedProductName = blankToNull(productName);
        String normalizedBarcode = blankToNull(barcode);
        String normalizedCategory = blankToNull(category);
        String normalizedSalesmanId = blankToNull(salesmanId);

        for (ProductSalesAggProjection row : billRepository.findProductSales(
                tenantId,
                start,
                end,
                normalizedProductName,
                normalizedBarcode,
                normalizedCategory,
                normalizedSalesmanId)) {
            ProductSalesReportResponse.ProductSalesItem item = new ProductSalesReportResponse.ProductSalesItem();
            item.setProductId(row.getProductId());
            item.setProductName(row.getProductName());
            item.setBarcode(row.getBarcode());
            item.setCategory(row.getCategory());
            item.setHsnCode(row.getHsnCode());
            item.setCurrentStock(row.getCurrentStock());
            item.setQuantitySold(round2(nonNull(row.getQuantitySold())));
            item.setBillsCount(row.getBillsCount() != null ? row.getBillsCount() : 0L);
            item.setGrossRevenue(round2(nonNull(row.getGrossRevenue())));
            item.setDiscountAmount(round2(nonNull(row.getDiscountAmount())));
            item.setTaxableRevenue(round2(nonNull(row.getTaxableRevenue())));
            item.setGstAmount(round2(nonNull(row.getGstAmount())));
            item.setNetRevenue(round2(nonNull(row.getNetRevenue())));
            item.setAverageSellingPrice(item.getQuantitySold() > 0.0
                    ? round2(item.getNetRevenue() / item.getQuantitySold())
                    : 0.0);
            items.add(item);
        }

        ProductSalesReportResponse response = new ProductSalesReportResponse();
        response.setPeriodStart(start.toString());
        response.setPeriodEnd(end.toString());
        response.setItems(items);
        response.setProducts(items.size());
        response.setQuantitySold(round2(items.stream().mapToDouble(ProductSalesReportResponse.ProductSalesItem::getQuantitySold).sum()));
        response.setGrossRevenue(round2(items.stream().mapToDouble(ProductSalesReportResponse.ProductSalesItem::getGrossRevenue).sum()));
        response.setDiscountAmount(round2(items.stream().mapToDouble(ProductSalesReportResponse.ProductSalesItem::getDiscountAmount).sum()));
        response.setTaxableRevenue(round2(items.stream().mapToDouble(ProductSalesReportResponse.ProductSalesItem::getTaxableRevenue).sum()));
        response.setGstAmount(round2(items.stream().mapToDouble(ProductSalesReportResponse.ProductSalesItem::getGstAmount).sum()));
        response.setTotalRevenue(round2(items.stream().mapToDouble(ProductSalesReportResponse.ProductSalesItem::getNetRevenue).sum()));
        response.setBills(billRepository.countProductSalesBills(
                tenantId,
                start,
                end,
                normalizedProductName,
                normalizedBarcode,
                normalizedCategory,
                normalizedSalesmanId));
        response.setTopProductByRevenue(items.isEmpty() ? null : items.get(0));
        response.setTopProductByQuantity(items.stream()
                .max(Comparator.comparingDouble(ProductSalesReportResponse.ProductSalesItem::getQuantitySold))
                .orElse(null));
        return response;
    }

    private SalesReportResponse buildSalesReport(LocalDateTime start, LocalDateTime endExclusive) {
        SalesReportResponse response = new SalesReportResponse();
        response.setBills(billRepository.countByCreatedAtBetween(start, endExclusive));
        response.setTotalSales(
                billRepository.sumTotalAmountBetween(start, endExclusive) != null
                        ? billRepository.sumTotalAmountBetween(start, endExclusive)
                        : 0.0
        );
        response.setItems(
                billRepository.sumItemsQuantityBetween(start, endExclusive) != null
                        ? Math.round(billRepository.sumItemsQuantityBetween(start, endExclusive))
                        : 0L
        );

        List<DailyReportResponse.ProductSummary> productBreakdown = new ArrayList<>();
        for (ReportAggProjection row : billRepository.findProductBreakdown(start, endExclusive)) {
            productBreakdown.add(new DailyReportResponse.ProductSummary(
                    row.getName(),
                    row.getQty() != null ? Math.round(row.getQty().doubleValue()) : 0L
            ));
        }
        response.setProductBreakdown(productBreakdown);
        response.setTopProduct(productBreakdown.isEmpty() ? null : productBreakdown.get(0));

        List<DailyReportResponse.SalesmanSummary> salesmanBreakdown = new ArrayList<>();
        for (ReportAggProjection row : billRepository.findSalesmanBreakdown(start, endExclusive)) {
            salesmanBreakdown.add(new DailyReportResponse.SalesmanSummary(
                    row.getName() != null ? row.getName() : "Unknown",
                    row.getEmployeeId(),
                    row.getRevenue() != null ? row.getRevenue() : 0.0
            ));
        }
        response.setSalesmanBreakdown(salesmanBreakdown);
        response.setTopSalesman(salesmanBreakdown.isEmpty() ? null : salesmanBreakdown.get(0));

        return response;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static double nonNull(Double amount) {
        return amount != null ? amount : 0.0;
    }

    private static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    public List<SalesReportDTO> getSalesReport(String start, String end) {

        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);

        List<Object[]> results =
                orderRepository.findSalesSummary(startDate, endDate);

        return results.stream()
                .map(r -> new SalesReportDTO(
                        r[0].toString(),
                        ((Number) r[1]).doubleValue(),
                        ((Number) r[2]).longValue()
                ))
                .toList();
    }

    @Cacheable(cacheNames = "reports", key = "T(com.pahal.billingApp.context.TenantContext).getCurrentTenant() + ':dayEnd:' + #day")
    public DayEndReportResponse buildDayEndReport(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        String storeId = TenantContext.getCurrentTenant();
        DayEndReportResponse response = new DayEndReportResponse();
        response.setStoreId(storeId);
        response.setDate(day.toString());

        double totalCollected = 0.0;
        for (PaymentMethodAggProjection row : billRepository.sumPaymentsByMethodExcluding(storeId, start, end, PaymentMethod.CREDIT)) {
            PaymentMethod method = row.getMethod();
            double amount = row.getAmount() != null ? row.getAmount() : 0.0;
            response.getCollectionsByMethod().put(method, amount);
            totalCollected += amount;
        }
        response.setTotalCollected(Math.round(totalCollected * 100.0) / 100.0);

        Double credit = billRepository.sumPaymentsByMethod(storeId, start, end, PaymentMethod.CREDIT);
        response.setPendingCredit(Math.round((credit != null ? credit : 0.0) * 100.0) / 100.0);

        Map<String, DayEndReportResponse.SalesmanDayEnd> bySalesman = new LinkedHashMap<>();

        for (SalesmanPaymentAggProjection row : billRepository.sumSalesmanPaymentsByMethodExcluding(storeId, start, end, PaymentMethod.CREDIT)) {
            String employeeId = row.getEmployeeId();
            if (employeeId == null) continue;
            DayEndReportResponse.SalesmanDayEnd s = bySalesman.computeIfAbsent(employeeId, id -> {
                DayEndReportResponse.SalesmanDayEnd x = new DayEndReportResponse.SalesmanDayEnd();
                x.setEmployeeId(id);
                x.setName(row.getName() != null ? row.getName() : "Unknown");
                return x;
            });

            PaymentMethod method = row.getMethod();
            double amount = row.getAmount() != null ? row.getAmount() : 0.0;
            s.getCollectionsByMethod().put(method, amount);
            s.setTotalCollected(Math.round((s.getTotalCollected() + amount) * 100.0) / 100.0);
        }

        for (SalesmanPaymentAggProjection row : billRepository.sumSalesmanPaymentsByMethod(storeId, start, end, PaymentMethod.CREDIT)) {
            String employeeId = row.getEmployeeId();
            if (employeeId == null) continue;
            DayEndReportResponse.SalesmanDayEnd s = bySalesman.computeIfAbsent(employeeId, id -> {
                DayEndReportResponse.SalesmanDayEnd x = new DayEndReportResponse.SalesmanDayEnd();
                x.setEmployeeId(id);
                x.setName(row.getName() != null ? row.getName() : "Unknown");
                return x;
            });
            double amount = row.getAmount() != null ? row.getAmount() : 0.0;
            s.setPendingCredit(Math.round(amount * 100.0) / 100.0);
        }

        response.setSalesmen(new ArrayList<>(bySalesman.values()));
        return response;
    }
}
