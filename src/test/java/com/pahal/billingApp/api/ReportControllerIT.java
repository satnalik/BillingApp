package com.pahal.billingApp.api;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.BillPayment;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.enums.ItemType;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.ProductRepository;
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
    @Autowired ProductRepository productRepository;

    @Test
    void productSales_returnsProductWiseRevenueAndDoesNotLeakAcrossTenants() throws Exception {
        LocalDate day = LocalDate.now();
        String tenant = "Tenant-Product-Sales";
        String otherTenant = "Tenant-Product-Sales-Other";

        TenantContext.setCurrentTenant(tenant);
        try {
            Salesman salesman = new Salesman();
            salesman.setEmployeeId("PS-1");
            salesman.setName("Product Seller");
            salesManRepository.save(salesman);

            Product widget = product("PS-WIDGET", "Widget", "Tools", 7.0);
            widget = productRepository.save(widget);

            Product gadget = product("PS-GADGET", "Gadget", "Tools", 3.0);
            gadget = productRepository.save(gadget);

            Bill firstBill = bill(salesman, 262.4, List.of(
                    item(widget, 2.0, 100.0, 10.0, 180.0, 32.4),
                    item(gadget, 1.0, 50.0, 0.0, 50.0, 0.0)
            ));
            billRepository.save(firstBill);

            Bill secondBill = bill(salesman, 141.6, List.of(
                    item(widget, 1.0, 120.0, 0.0, 120.0, 21.6)
            ));
            billRepository.save(secondBill);
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(otherTenant);
        try {
            Salesman salesman = new Salesman();
            salesman.setEmployeeId("PS-OTHER");
            salesman.setName("Other Seller");
            salesManRepository.save(salesman);

            Product product = product("PS-OTHER", "Other Widget", "Tools", 99.0);
            product = productRepository.save(product);
            billRepository.save(bill(salesman, 999.0, List.of(
                    item(product, 1.0, 999.0, 0.0, 999.0, 0.0)
            )));
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(tenant);
        try {
            mockMvc.perform(get("/api/reports/product-sales")
                            .param("from", day.toString())
                            .param("to", day.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRevenue").value(404.0))
                    .andExpect(jsonPath("$.taxableRevenue").value(350.0))
                    .andExpect(jsonPath("$.gstAmount").value(54.0))
                    .andExpect(jsonPath("$.grossRevenue").value(370.0))
                    .andExpect(jsonPath("$.discountAmount").value(20.0))
                    .andExpect(jsonPath("$.quantitySold").value(4.0))
                    .andExpect(jsonPath("$.bills").value(2))
                    .andExpect(jsonPath("$.products").value(2))
                    .andExpect(jsonPath("$.topProductByRevenue.productName").value("Widget"))
                    .andExpect(jsonPath("$.topProductByQuantity.productName").value("Widget"))
                    .andExpect(jsonPath("$.items[?(@.productName=='Widget' && @.quantitySold==3.0 && @.billsCount==2 && @.netRevenue==354.0 && @.averageSellingPrice==118.0 && @.currentStock==7.0)]").exists())
                    .andExpect(jsonPath("$.items[?(@.productName=='Gadget' && @.quantitySold==1.0 && @.billsCount==1 && @.netRevenue==50.0)]").exists())
                    .andExpect(jsonPath("$.items[?(@.productName=='Other Widget')]").doesNotExist());

            TenantContext.setCurrentTenant(tenant);
            mockMvc.perform(get("/api/reports/product-sales")
                            .param("from", day.toString())
                            .param("to", day.toString())
                            .param("productName", "wid"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRevenue").value(354.0))
                    .andExpect(jsonPath("$.products").value(1))
                    .andExpect(jsonPath("$.items[0].productName").value("Widget"));
        } finally {
            TenantContext.clear();
        }
    }

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

    private Product product(String barcode, String name, String category, double stockQuantity) {
        Product product = new Product();
        product.setBarcode(barcode);
        product.setName(name);
        product.setCategory(category);
        product.setHsnCode("HSN-" + barcode);
        product.setSellingPrice(100.0);
        product.setPrice(100.0);
        product.setItemType(ItemType.PACKAGE);
        product.setStockQuantity(stockQuantity);
        return product;
    }

    private Bill bill(Salesman salesman, double totalAmount, List<BillItem> items) {
        Bill bill = new Bill();
        bill.setCustomerName("Product Sales Customer");
        bill.setContactInfo("9000000000");
        bill.setSalesMan(salesman);
        bill.setSubTotalAmount(items.stream().mapToDouble(i -> i.getTaxableAmount() != null ? i.getTaxableAmount() : 0.0).sum());
        bill.setGstAmount(items.stream().mapToDouble(i -> i.getGstAmount() != null ? i.getGstAmount() : 0.0).sum());
        bill.setGstApplied(bill.getGstAmount() > 0.0);
        bill.setTotalAmount(totalAmount);
        bill.setPaidAmount(totalAmount);
        bill.setDueAmount(0.0);
        bill.setItems(items);
        return bill;
    }

    private BillItem item(Product product, double quantity, double unitPrice, double discount, double taxableAmount, double gstAmount) {
        BillItem item = new BillItem();
        item.setProductId(product.getId());
        item.setBarcode(product.getBarcode());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitSellingPrice(unitPrice);
        item.setPriceAtSale(unitPrice);
        item.setDiscount(discount);
        item.setHsnCode(product.getHsnCode());
        item.setGstRate(gstAmount > 0.0 && taxableAmount > 0.0 ? gstAmount / taxableAmount : 0.0);
        item.setTaxableAmount(taxableAmount);
        item.setGstAmount(gstAmount);
        return item;
    }
}
