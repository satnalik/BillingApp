package com.pahal.billingApp.service;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.dto.DashboardTodayResponse;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.enums.PurchaseStatus;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.PurchaseBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class DashboardService {

    private final BillRepository billRepository;
    private final ProductRepository productRepository;
    private final PurchaseBillRepository purchaseBillRepository;

    public DashboardService(
            BillRepository billRepository,
            ProductRepository productRepository,
            PurchaseBillRepository purchaseBillRepository) {
        this.billRepository = billRepository;
        this.productRepository = productRepository;
        this.purchaseBillRepository = purchaseBillRepository;
    }

    @Transactional(readOnly = true)
    public DashboardTodayResponse buildTodayDashboard(LocalDate date, Double lowStockThreshold) {
        LocalDate dashboardDate = date != null ? date : LocalDate.now();
        double threshold = lowStockThreshold != null ? lowStockThreshold : 10.0;

        LocalDateTime start = dashboardDate.atStartOfDay();
        LocalDateTime end = dashboardDate.plusDays(1).atStartOfDay();
        String tenantId = TenantContext.getCurrentTenant();

        DashboardTodayResponse response = new DashboardTodayResponse();
        response.setDate(dashboardDate.toString());
        response.setTodaySales(round2(nonNull(billRepository.sumTotalAmountBetween(start, end))));
        response.setBillCount(billRepository.countByCreatedAtBetween(start, end));
        response.setCustomerDue(round2(nonNull(billRepository.sumOutstandingDueAmount())));
        response.setSupplierDue(
                round2(nonNull(purchaseBillRepository.sumActiveSupplierDueAmount(PurchaseStatus.CANCELLED))));
        response.setLowStockThreshold(threshold);
        response.setLowStockCount(productRepository.countByStockQuantityLessThanEqual(threshold));

        for (PaymentMethod method : PaymentMethod.values()) {
            Double amount = tenantId != null
                    ? billRepository.sumPaymentsByMethod(tenantId, start, end, method)
                    : 0.0;
            response.getPaymentSplit().put(method, round2(nonNull(amount)));
        }

        response.setLowStockItems(
                productRepository.findTop10ByStockQuantityLessThanEqualOrderByStockQuantityAsc(threshold)
                        .stream()
                        .map(this::toLowStockProduct)
                        .toList());

        return response;
    }

    private DashboardTodayResponse.LowStockProduct toLowStockProduct(Product product) {
        DashboardTodayResponse.LowStockProduct item = new DashboardTodayResponse.LowStockProduct();
        item.setId(product.getId());
        item.setName(product.getName());
        item.setBarcode(product.getBarcode());
        item.setStockQuantity(product.getStockQuantity());
        item.setCategory(product.getCategory());
        item.setSupplierName(product.getSupplierName());
        return item;
    }

    private static double nonNull(Double amount) {
        return amount != null ? amount : 0.0;
    }

    private static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
