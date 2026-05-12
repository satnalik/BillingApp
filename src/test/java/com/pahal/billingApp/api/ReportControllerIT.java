package com.pahal.billingApp.api;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.BillPayment;
import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.SalesManRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReportControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired SalesManRepository salesManRepository;
    @Autowired BillRepository billRepository;

    @Test
    void dayEnd_doesNotLeakAcrossTenants() throws Exception {
        LocalDate day = LocalDate.now();

        seedTenant("Tenant-A", "A1", "Alice", 100.0, 0.0);
        seedTenant("Tenant-B", "B1", "Bob", 999.0, 0.0);

        TenantContext.setCurrentTenant("Tenant-A");
        try {
            mockMvc.perform(get("/api/reports/day-end")
                            .param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value("Tenant-A"))
                .andExpect(jsonPath("$.salesmen").isArray())
                .andExpect(jsonPath("$.salesmen[?(@.employeeId=='A1')]").exists())
                .andExpect(jsonPath("$.salesmen[?(@.employeeId=='B1')]").doesNotExist());
        } finally {
            TenantContext.clear();
        }
    }

    private void seedTenant(String tenantId, String employeeId, String name, double cashAmount, double creditAmount) {
        TenantContext.setCurrentTenant(tenantId);
        try {
            Salesman s = new Salesman();
            s.setEmployeeId(employeeId);
            s.setName(name);
            salesManRepository.save(s);

            Bill b = new Bill();
            b.setCustomerName("C");
            b.setContactInfo("X");
            b.setSalesMan(s);
            b.setTotalAmount(cashAmount + creditAmount);
            b.setPaidAmount(cashAmount);
            b.setDueAmount(creditAmount);

            BillItem item = new BillItem();
            item.setProductName("P");
            item.setQuantity(1.0);
            item.setDiscount(0.0);
            item.setUnitSellingPrice(1.0);
            b.setItems(List.of(item));

            if (cashAmount > 0) {
                BillPayment p = new BillPayment();
                p.setBill(b);
                p.setMethod(PaymentMethod.CASH);
                p.setAmount(cashAmount);
                b.getPayments().add(p);
            }
            if (creditAmount > 0) {
                BillPayment p = new BillPayment();
                p.setBill(b);
                p.setMethod(PaymentMethod.CREDIT);
                p.setAmount(creditAmount);
                b.getPayments().add(p);
            }

            billRepository.save(b);
        } finally {
            TenantContext.clear();
        }
    }
}
