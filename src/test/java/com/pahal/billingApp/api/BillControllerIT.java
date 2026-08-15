package com.pahal.billingApp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.dto.AddBillPaymentRequest;
import com.pahal.billingApp.dto.CreateBillItemRequest;
import com.pahal.billingApp.dto.CreateBillPaymentRequest;
import com.pahal.billingApp.dto.CreateBillRequest;
import com.pahal.billingApp.entity.Customer;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.ProductBarcode;
import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.BillPayment;
import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.CustomerRepository;
import com.pahal.billingApp.service.BillingService;
import com.pahal.billingApp.repository.ProductBarcodeRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SalesManRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class BillControllerIT {

    private static final String TENANT = "Tenant-A";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductBarcodeRepository productBarcodeRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesManRepository salesManRepository;
    @Autowired BillRepository billRepository;

    @Test
    void billRegister_filtersAndReturnsSummary() throws Exception {
        final String registerTenant = "Tenant-Register";

        TenantContext.setCurrentTenant(registerTenant);
        long billId;
        try {
            Bill bill = new Bill();
            bill.setCustomerName("Register Customer");
            bill.setContactInfo("9990001111");
            bill.setTotalAmount(500.0);
            bill.setPaidAmount(300.0);
            bill.setDueAmount(200.0);

            BillItem item = new BillItem();
            item.setProductName("Register Product");
            item.setQuantity(1.0);
            item.setUnitSellingPrice(500.0);
            item.setPriceAtSale(500.0);
            bill.setItems(List.of(item));

            BillPayment cash = new BillPayment();
            cash.setBill(bill);
            cash.setMethod(com.pahal.billingApp.enums.PaymentMethod.CASH);
            cash.setAmount(300.0);

            BillPayment credit = new BillPayment();
            credit.setBill(bill);
            credit.setMethod(com.pahal.billingApp.enums.PaymentMethod.CREDIT);
            credit.setAmount(200.0);
            bill.setPayments(new LinkedHashSet<>(List.of(cash, credit)));

            billId = billRepository.save(bill).getId();
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(registerTenant);
        try {
            mockMvc.perform(get("/api/bills/register")
                            .param("billNo", String.format("INV-%08d", billId))
                            .param("dueOnly", "true")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(billId))
                    .andExpect(jsonPath("$.items[0].billNumber").value(String.format("INV-%08d", billId)))
                    .andExpect(jsonPath("$.items[0].customerName").value("Register Customer"))
                    .andExpect(jsonPath("$.items[0].itemsCount").value(1))
                    .andExpect(jsonPath("$.items[0].paymentMethods[?(@=='CASH')]").exists())
                    .andExpect(jsonPath("$.items[0].paymentMethods[?(@=='CREDIT')]").exists());

            mockMvc.perform(get("/api/bills/register/summary")
                            .param("billNo", String.format("INV-%08d", billId))
                            .param("dueOnly", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBills").value(1))
                    .andExpect(jsonPath("$.totalSales").value(500.0))
                    .andExpect(jsonPath("$.totalPaid").value(300.0))
                    .andExpect(jsonPath("$.totalDue").value(200.0))
                    .andExpect(jsonPath("$.paymentSplit.CASH").value(300.0))
                    .andExpect(jsonPath("$.paymentSplit.CREDIT").value(200.0));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void createBill_thenFetchById_includesPaymentsAndBillNumber() throws Exception {
        // Seed product + salesman for this tenant.
        Product p = new Product();
        p.setBarcode("P1");
        p.setName("Dove");
        p.setSellingPrice(2.0);
        p.setPrice(2.0);
        p.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
        p.setStockQuantity(100.0);
        TenantContext.setCurrentTenant(TENANT);
        try {
            productRepository.save(p);
        } finally {
            TenantContext.clear();
        }

        Salesman s = new Salesman();
        s.setEmployeeId("68328");
        s.setName("Damu");
        TenantContext.setCurrentTenant(TENANT);
        try {
            salesManRepository.save(s);
        } finally {
            TenantContext.clear();
        }

        CreateBillItemRequest item = new CreateBillItemRequest();
        item.setProductName("Dove");
        item.setQuantity(4.0);
        item.setDiscount(0.0);
        item.setUnitSellingPrice(2.0);


        CreateBillRequest req = new CreateBillRequest();
        req.setCustomerName("Cust");
        req.setContactInfo("999");
        req.setSalesmanEmployeeId("68328");
        req.setItems(List.of(item));
        req.setPayments(null);

        TenantContext.setCurrentTenant(TENANT);
        String responseJson;
        try {
            responseJson = mockMvc.perform(
                        post("/api/bills")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.billNumber").isString())
                .andExpect(jsonPath("$.payments").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();
        } finally {
            TenantContext.clear();
        }

        Long id = objectMapper.readTree(responseJson).get("id").asLong();

        TenantContext.setCurrentTenant(TENANT);
        try {
        mockMvc.perform(get("/api/bills/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.billNumber").value(String.format("INV-%08d", id)))
                .andExpect(jsonPath("$.items[0].productName").value("Dove"))
                .andExpect(jsonPath("$.payments[?(@.method=='CREDIT')]").exists());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void collectDue_addsPaymentAndReducesDue() throws Exception {
        // Seed product + salesman for this tenant.
        TenantContext.setCurrentTenant(TENANT);
        try {
            Product p = new Product();
            p.setBarcode("P2");
            p.setName("Soap");
            p.setSellingPrice(100.0);
            p.setPrice(100.0);
            p.setGstRate(0.18);
            p.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            p.setStockQuantity(10.0);
            productRepository.save(p);

            Salesman s = new Salesman();
            s.setEmployeeId("77777");
            s.setName("Cashier");
            salesManRepository.save(s);
        } finally {
            TenantContext.clear();
        }

        CreateBillItemRequest item = new CreateBillItemRequest();
        item.setProductName("Soap");
        item.setQuantity(1.0);
        item.setDiscount(0.0);
        item.setUnitSellingPrice(100.0);

        CreateBillRequest req = new CreateBillRequest();
        req.setCustomerName("Cust2");
        req.setContactInfo("888");
        req.setSalesmanEmployeeId("77777");
        req.setItems(List.of(item));
        req.setPayments(null); // creates full CREDIT due

        TenantContext.setCurrentTenant(TENANT);
        long billId;
        try {
            String responseJson = mockMvc.perform(
                            post("/api/bills")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dueAmount").isNumber())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            billId = objectMapper.readTree(responseJson).get("id").asLong();
        } finally {
            TenantContext.clear();
        }

        AddBillPaymentRequest payment = new AddBillPaymentRequest();
        payment.setMethod(com.pahal.billingApp.enums.PaymentMethod.CASH);
        payment.setAmount(50.0);

        TenantContext.setCurrentTenant(TENANT);
        try {
            mockMvc.perform(
                            post("/api/bills/{id}/payments", billId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(payment))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(billId))
                    .andExpect(jsonPath("$.paidAmount").value(50.0))
                    .andExpect(jsonPath("$.dueAmount").isNumber())
                    .andExpect(jsonPath("$.payments[?(@.method=='CASH')]").exists())
                    .andExpect(jsonPath("$.payments[?(@.method=='CREDIT')]").exists());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void collectDue_multipleTimes_reducesDue_andStopsAtZero() throws Exception {
        // Seed product + salesman for this tenant.
        TenantContext.setCurrentTenant(TENANT);
        try {
            Product p = new Product();
            p.setBarcode("P3");
            p.setName("Shampoo");
            p.setSellingPrice(100.0);
            p.setPrice(100.0);
            p.setGstRate(0.18);
            p.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            p.setStockQuantity(10.0);
            productRepository.save(p);

            Salesman s = new Salesman();
            s.setEmployeeId("99999");
            s.setName("Cashier2");
            salesManRepository.save(s);
        } finally {
            TenantContext.clear();
        }

        CreateBillItemRequest item = new CreateBillItemRequest();
        item.setProductName("Shampoo");
        item.setQuantity(1.0);
        item.setDiscount(0.0);
        item.setUnitSellingPrice(100.0);

        CreateBillRequest req = new CreateBillRequest();
        req.setCustomerName("Cust3");
        req.setContactInfo("777");
        req.setSalesmanEmployeeId("99999");
        req.setItems(List.of(item));
        req.setPayments(null); // creates full CREDIT due

        long billId;
        TenantContext.setCurrentTenant(TENANT);
        try {
            String responseJson = mockMvc.perform(
                            post("/api/bills")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dueAmount").value(118.0))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            billId = objectMapper.readTree(responseJson).get("id").asLong();
        } finally {
            TenantContext.clear();
        }

        AddBillPaymentRequest payment = new AddBillPaymentRequest();
        payment.setMethod(com.pahal.billingApp.enums.PaymentMethod.CASH);
        payment.setAmount(60.0);

        TenantContext.setCurrentTenant(TENANT);
        try {
            mockMvc.perform(
                            post("/api/bills/{id}/payments", billId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(payment))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paidAmount").value(60.0))
                    .andExpect(jsonPath("$.dueAmount").value(58.0));
        } finally {
            TenantContext.clear();
        }

        payment.setAmount(58.0);
        TenantContext.setCurrentTenant(TENANT);
        try {
            mockMvc.perform(
                            post("/api/bills/{id}/payments", billId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(payment))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paidAmount").value(118.0))
                    .andExpect(jsonPath("$.dueAmount").value(0.0));
        } finally {
            TenantContext.clear();
        }

        // Once due is 0, any further collection must be rejected.
        payment.setAmount(1.0);
        TenantContext.setCurrentTenant(TENANT);
        try {
            org.junit.jupiter.api.Assertions.assertThrows(
                    Exception.class,
                    () -> mockMvc.perform(
                                    post("/api/bills/{id}/payments", billId)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(objectMapper.writeValueAsString(payment))
                            ).andReturn()
            );
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(TENANT);
        try {
            var bill = billRepository.findWithDetailsById(billId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(0.0, bill.getDueAmount());
            org.junit.jupiter.api.Assertions.assertEquals(118.0, bill.getPaidAmount());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void createBill_withInstantDiscount_reducesTotal_andKeepsCreditDueAsUsual() throws Exception {
        final String GROCERY_TENANT = "Tenant-Grocery";
        // Seed product + salesman for this tenant.
        TenantContext.setCurrentTenant(GROCERY_TENANT);
        try {
            Product p = new Product();
            p.setBarcode("P4");
            p.setName("Dha_Ref_oil_1lt");
            p.setSellingPrice(198.0);
            p.setPrice(198.0);
            p.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            p.setStockQuantity(10.0);
            productRepository.save(p);

            Salesman s = new Salesman();
            s.setEmployeeId("G1");
            s.setName("Damu");
            s.setTenantId(GROCERY_TENANT);
            salesManRepository.save(s);
        } finally {
            TenantContext.clear();
        }

        CreateBillItemRequest item = new CreateBillItemRequest();
        item.setProductName("Dha_Ref_oil_1lt");
        item.setQuantity(1.0);
        item.setDiscount(0.0);
        item.setUnitSellingPrice(198.0);

        CreateBillRequest req = new CreateBillRequest();
        req.setCustomerName("Cust4");
        req.setContactInfo("111");
        req.setSalesmanEmployeeId("G1");
        req.setItems(List.of(item));
        req.setInstantDiscountAmount(15.0);

        CreateBillPaymentRequest cash = new CreateBillPaymentRequest();
        cash.setMethod(com.pahal.billingApp.enums.PaymentMethod.CASH);
        cash.setAmount(183.0);

        req.setPayments(List.of(cash));

        TenantContext.setCurrentTenant(GROCERY_TENANT);
        try {
            mockMvc.perform(
                            post("/api/bills")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.gstApplied").value(false))
                    .andExpect(jsonPath("$.subTotalAmount").value(198.0))
                    .andExpect(jsonPath("$.instantDiscountAmount").value(15.0))
                    .andExpect(jsonPath("$.totalAmount").value(183.0))
                    .andExpect(jsonPath("$.paidAmount").value(183.0))
                    .andExpect(jsonPath("$.dueAmount").value(0.0))
                    .andExpect(jsonPath("$.payments[?(@.method=='CASH')]").exists())
                    .andExpect(jsonPath("$.payments[?(@.method=='CREDIT')]").doesNotExist());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void createBill_byAliasBarcode_appliesQuantityPerScanAndCreatesCustomerOnce() throws Exception {
        final String barcodeTenant = "Tenant-Barcode-Bill";

        long productId;
        TenantContext.setCurrentTenant(barcodeTenant);
        try {
            Product product = new Product();
            product.setBarcode("BISCUIT-OLD-BOX");
            product.setName("Biscuit Pack");
            product.setSellingPrice(5.0);
            product.setPrice(5.0);
            product.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            product.setStockQuantity(25.0);
            product = productRepository.save(product);
            productId = product.getId();

            ProductBarcode alias = new ProductBarcode();
            alias.setProduct(product);
            alias.setBarcode("BISCUIT-NEW-BOX");
            alias.setQuantityPerScan(10.0);
            alias.setPrimaryBarcode(false);
            productBarcodeRepository.save(alias);

            Salesman salesman = new Salesman();
            salesman.setEmployeeId("BILL-BC");
            salesman.setName("Barcode Cashier");
            salesManRepository.save(salesman);
        } finally {
            TenantContext.clear();
        }

        CreateBillItemRequest item = new CreateBillItemRequest();
        item.setBarcode("BISCUIT-NEW-BOX");
        item.setQuantity(1.0);
        item.setDiscount(0.0);
        item.setUnitSellingPrice(5.0);

        CreateBillRequest req = new CreateBillRequest();
        req.setCustomerName("Ravi Customer");
        req.setContactInfo("9000011111");
        req.setSalesmanEmployeeId("BILL-BC");
        req.setItems(List.of(item));

        TenantContext.setCurrentTenant(barcodeTenant);
        try {
            mockMvc.perform(
                            post("/api/bills")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].productId").value(productId))
                    .andExpect(jsonPath("$.items[0].barcode").value("BISCUIT-NEW-BOX"))
                    .andExpect(jsonPath("$.items[0].quantity").value(10.0))
                    .andExpect(jsonPath("$.totalAmount").value(50.0));

            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStockQuantity()).isEqualTo(15.0);

            Customer customer = customerRepository.findByContactNumber("9000011111").orElseThrow();
            assertThat(customer.getName()).isEqualTo("Ravi Customer");
            assertThat(customer.getCreatedAt()).isNotNull();
            Long customerId = customer.getId();

            TenantContext.setCurrentTenant(barcodeTenant);
            mockMvc.perform(
                            post("/api/bills")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req))
                    )
                    .andExpect(status().isOk());

            TenantContext.setCurrentTenant(barcodeTenant);
            assertThat(customerRepository.findByContactNumber("9000011111")).isPresent()
                    .get()
                    .extracting(Customer::getId)
                    .isEqualTo(customerId);
            assertThat(customerRepository.findAll()).hasSize(1);
        } finally {
            TenantContext.clear();
        }
    }
}
