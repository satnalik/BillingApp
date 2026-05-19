package com.pahal.billingApp.api;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillPayment;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.PurchaseBill;
import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.enums.ItemType;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.PurchaseBillRepository;
import com.pahal.billingApp.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DashboardControllerIT {

    private static final String TENANT = "Tenant-Dashboard";

    @Autowired MockMvc mockMvc;
    @Autowired BillRepository billRepository;
    @Autowired ProductRepository productRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseBillRepository purchaseBillRepository;

    @Test
    void todayDashboard_returnsSalesPaymentsDuesAndLowStock() throws Exception {
        LocalDate today = LocalDate.now();

        TenantContext.setCurrentTenant(TENANT);
        try {
            Bill bill = new Bill();
            bill.setCustomerName("Customer");
            bill.setTotalAmount(500.0);
            bill.setPaidAmount(300.0);
            bill.setDueAmount(200.0);

            BillPayment cash = new BillPayment();
            cash.setBill(bill);
            cash.setMethod(PaymentMethod.CASH);
            cash.setAmount(300.0);

            BillPayment credit = new BillPayment();
            credit.setBill(bill);
            credit.setMethod(PaymentMethod.CREDIT);
            credit.setAmount(200.0);
            bill.setPayments(new LinkedHashSet<>(List.of(cash, credit)));
            billRepository.save(bill);

            Product lowStockProduct = new Product();
            lowStockProduct.setBarcode("DASH-P1");
            lowStockProduct.setName("Low Stock Soap");
            lowStockProduct.setItemType(ItemType.PACKAGE);
            lowStockProduct.setStockQuantity(3.0);
            productRepository.save(lowStockProduct);

            Supplier supplier = new Supplier();
            supplier.setName("Dashboard Supplier");
            supplier.setSupplierCode("DASH");
            supplier = supplierRepository.save(supplier);

            PurchaseBill purchaseBill = new PurchaseBill();
            purchaseBill.setSupplier(supplier);
            purchaseBill.setBillNumber("DASH-1");
            purchaseBill.setBillDate(today);
            purchaseBill.setSubTotalAmount(1000.0);
            purchaseBill.setDiscountAmount(0.0);
            purchaseBill.setTaxAmount(0.0);
            purchaseBill.setTotalAmount(1000.0);
            purchaseBill.setPaidAmount(250.0);
            purchaseBill.setDueAmount(750.0);
            purchaseBillRepository.save(purchaseBill);
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(TENANT);
        try {
            mockMvc.perform(get("/api/dashboard/today")
                            .param("date", today.toString())
                            .param("lowStockThreshold", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value(today.toString()))
                    .andExpect(jsonPath("$.todaySales").value(500.0))
                    .andExpect(jsonPath("$.billCount").value(1))
                    .andExpect(jsonPath("$.paymentSplit.CASH").value(300.0))
                    .andExpect(jsonPath("$.paymentSplit.CREDIT").value(200.0))
                    .andExpect(jsonPath("$.customerDue").value(200.0))
                    .andExpect(jsonPath("$.supplierDue").value(750.0))
                    .andExpect(jsonPath("$.lowStockCount").value(1))
                    .andExpect(jsonPath("$.lowStockItems[0].name").value("Low Stock Soap"));
        } finally {
            TenantContext.clear();
        }
    }
}
